/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.BaseAssetDao;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
import cz.cvut.kbss.termit.service.term.AssertedInferredValueDifferentiator;
import cz.cvut.kbss.termit.service.term.OrphanedInverseTermRelationshipRemover;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import jakarta.validation.Validator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Service
public class TermRepositoryService extends BaseAssetRepositoryService<Term, TermDto> implements SnapshotProvider<Term> {

    private final IdentifierResolver idResolver;

    private final Configuration config;

    private final TermDao termDao;

    private final OrphanedInverseTermRelationshipRemover orphanedRelationshipRemover;

    private final VocabularyRepositoryService vocabularyService;

    private final TermOccurrenceService termOccurrenceService;

    public TermRepositoryService(Validator validator, IdentifierResolver idResolver,
                                 Configuration config, TermDao termDao,
                                 OrphanedInverseTermRelationshipRemover orphanedRelationshipRemover,
                                 TermOccurrenceService termOccurrenceService,
                                 VocabularyRepositoryService vocabularyService) {
        super(validator);
        this.idResolver = idResolver;
        this.config = config;
        this.termDao = termDao;
        this.orphanedRelationshipRemover = orphanedRelationshipRemover;
        this.vocabularyService = vocabularyService;
        this.termOccurrenceService = termOccurrenceService;
    }

    @Override
    protected BaseAssetDao<Term> getPrimaryDao() {
        return termDao;
    }

    @Override
    protected TermDto mapToDto(Term entity) {
        return new TermDto(entity);
    }

    @Override
    public void persist(@Nonnull Term instance) {
        throw new UnsupportedOperationException(
                "Persisting term by itself is not supported. It has to be connected to a vocabulary or a parent term.");
    }

    @Override
    protected void preUpdate(@Nonnull Term instance) {
        final Vocabulary vocabulary = vocabularyService.getReference(instance.getVocabulary());
        instance.setPrimaryLanguage(vocabulary.getPrimaryLanguage());
        super.preUpdate(instance);
        // Existence check is done as part of super.preUpdate
        final Term original = termDao.find(instance.getUri()).get();
        SnapshotProvider.verifySnapshotNotModified(original);
        termDao.detach(original);
        final AssertedInferredValueDifferentiator differentiator = new AssertedInferredValueDifferentiator();
        differentiator.differentiateRelatedTerms(instance, original);
        differentiator.differentiateRelatedMatchTerms(instance, original);
        differentiator.differentiateExactMatchTerms(instance, original);
        orphanedRelationshipRemover.removeOrphanedInverseTermRelationships(instance, original);
        instance.splitExternalAndInternalParents();
        pruneEmptyTranslations(instance);
    }

    private void pruneEmptyTranslations(Term instance) {
        assert instance != null;
        Utils.pruneBlankTranslations(instance.getLabel());
        Utils.pruneBlankTranslations(instance.getDefinition());
        Utils.pruneBlankTranslations(instance.getDescription());
        Utils.emptyIfNull(instance.getAltLabels()).forEach(Utils::pruneBlankTranslations);
        Utils.emptyIfNull(instance.getHiddenLabels()).forEach(Utils::pruneBlankTranslations);
    }

    @Override
    protected void postUpdate(@Nonnull Term instance) {
        final Vocabulary vocabulary = vocabularyService.getReference(instance.getVocabulary());
        if (instance.hasParentInSameVocabulary()) {
            vocabulary.getGlossary().removeRootTerm(instance);
        } else {
            vocabulary.getGlossary().addRootTerm(instance);
        }
    }

    @Transactional
    public void setState(Term term, URI state) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(state);
        SnapshotProvider.verifySnapshotNotModified(term);
        termDao.setState(term, state);
    }

    @Transactional
    public void addRootTermToVocabulary(Term instance, Vocabulary vocabulary) {
        prepareTermForPersist(instance, vocabulary);
        instance.setGlossary(vocabulary.getGlossary().getUri());
        instance.splitExternalAndInternalParents();

        assert !instance.hasParentInSameVocabulary();
        addTermAsRootToGlossary(instance, vocabulary.getUri());
        termDao.persist(instance, vocabulary);
    }

    private void prepareTermForPersist(Term instance, Vocabulary vocabulary) {
        instance.setPrimaryLanguage(vocabulary.getPrimaryLanguage());
        validate(instance);

        if (instance.getUri() == null) {
            instance.setUri(generateIdentifier(vocabulary, instance.getLabel()));
        }
        verifyIdentifierUnique(instance);
        pruneEmptyTranslations(instance);
    }

    private URI generateIdentifier(Vocabulary vocabulary, MultilingualString termLabel) {
        return idResolver.generateDerivedIdentifier(vocabulary.getUri(), config.getNamespace().getTerm().getSeparator(),
                                                    termLabel.get(vocabulary.getPrimaryLabel()));
    }

    private void addTermAsRootToGlossary(Term instance, URI vocabularyIri) {
        // Load vocabulary so that it is managed and changes to it (resp. the glossary) are persisted on commit
        final Vocabulary toUpdate = vocabularyService.getReference(vocabularyIri);
        instance.setGlossary(toUpdate.getGlossary().getUri());
        toUpdate.getGlossary().addRootTerm(instance);
    }

    @Transactional
    public void addChildTerm(Term instance, Term parentTerm) {
        Objects.requireNonNull(instance);
        Objects.requireNonNull(parentTerm);

        SnapshotProvider.verifySnapshotNotModified(parentTerm);
        final URI vocabularyIri =
                instance.getVocabulary() != null ? instance.getVocabulary() : parentTerm.getVocabulary();

        final Vocabulary vocabulary = vocabularyService.getReference(vocabularyIri);
        prepareTermForPersist(instance, vocabulary);

        instance.setGlossary(vocabulary.getGlossary().getUri());
        instance.addParentTerm(parentTerm);
        instance.splitExternalAndInternalParents();
        if (!instance.hasParentInSameVocabulary()) {
            addTermAsRootToGlossary(instance, vocabularyIri);
        }

        termDao.persist(instance, vocabulary);
    }

    @Transactional(readOnly = true)
    public TermInfo findRequiredTermInfo(URI id) {
        return termDao.findTermInfo(id).orElseThrow(() -> NotFoundException.create(TermInfo.class.getSimpleName(), id));
    }

    /**
     * Gets all terms from a vocabulary, regardless of their position in the term hierarchy.
     * <p>
     * This returns all terms contained in a vocabulary's glossary.
     *
     * @param vocabulary Vocabulary whose terms should be returned. A reference is sufficient
     * @return List of term DTOs ordered by label
     * @see #findAllFull(Vocabulary)
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAll(Vocabulary vocabulary) {
        return termDao.findAll(vocabulary);
    }

    /**
     * Finds all terms in the specified vocabulary, regardless of their position in the term hierarchy. Filters terms
     * that have label and definition in the instance language.
     * <p>
     * Terms are loaded <b>without</b> their subterms.
     *
     * @param vocabulary Vocabulary whose terms to retrieve. A reference is sufficient
     * @return List of vocabulary term DTOs ordered by label
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAllWithDefinition(Vocabulary vocabulary) {
        return termDao.findAllWithDefinition(vocabulary);
    }

    /**
     * Gets all terms from a vocabulary, regardless of their position in the term hierarchy.
     * <p>
     * This returns the full versions of all terms (complete metadata) contained in a vocabulary's glossary and thus its
     * performance may be worse. If complete metadata are not required, use {@link #findAll(Vocabulary)}.
     *
     * @param vocabulary Vocabulary whose terms should be returned
     * @return List of full terms ordered by label
     * @see #findAll(Vocabulary)
     */
    public List<Term> findAllFull(Vocabulary vocabulary) {
        return termDao.findAllFull(vocabulary).stream().map(this::postLoad).collect(toList());
    }

    /**
     * Gets all terms from the specified vocabulary and its imports (transitive), regardless of their position in the
     * term hierarchy.
     * <p>
     * This returns all terms contained in the vocabulary glossaries.
     *
     * @param vocabulary Base vocabulary for the vocabulary import closure
     * @return List of terms ordered by label
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAllIncludingImported(Vocabulary vocabulary) {
        return termDao.findAllIncludingImported(vocabulary);
    }

    /**
     * Finds all root terms (terms without parent term) in the specified vocabulary.
     * <p>
     * Terms with a label in the instance language are prepended.
     *
     * @param vocabulary   Vocabulary whose terms should be returned
     * @param pageSpec     Page specifying result number and position
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching root terms
     * @see #findAllRootsIncludingImported(Vocabulary, Pageable, Collection)
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAllRoots(Vocabulary vocabulary, Pageable pageSpec,
                                      Collection<URI> includeTerms) {
        return termDao.findAllRoots(vocabulary, pageSpec, includeTerms);
    }

    /**
     * Finds all root terms (terms without parent term).
     * <p>
     * Terms with a label in the instance language are prepended.
     *
     * @param pageSpec     Page specifying result number and position
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching root terms
     * @see #findAllRootsIncludingImported(Vocabulary, Pageable, Collection)
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAllRoots(Pageable pageSpec,
                                      Collection<URI> includeTerms) {
        return termDao.findAllRoots(pageSpec, includeTerms);
    }

    /**
     * Finds all root terms (terms without parent term) in the specified vocabulary or any of its imported
     * vocabularies.
     * <p>
     * Basically, this does a transitive closure over the vocabulary import relationship, starting at the specified
     * vocabulary, and returns all parent-less terms.
     * <p>
     * Terms with a label in the instance language are prepended.
     *
     * @param vocabulary   Base vocabulary for the vocabulary import closure
     * @param pageSpec     Page specifying result number and position
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching root terms
     * @see #findAllRoots(Vocabulary, Pageable, Collection)
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAllRootsIncludingImported(Vocabulary vocabulary, Pageable pageSpec,
                                                       Collection<URI> includeTerms) {
        return termDao.findAllRootsIncludingImports(vocabulary, pageSpec, includeTerms);
    }

    /**
     * Finds all terms which match the specified search string in the specified vocabulary.
     *
     * @param searchString Search string
     * @param vocabulary   Vocabulary whose terms should be returned
     * @return Matching terms
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAll(String searchString, Vocabulary vocabulary) {
        return termDao.findAll(searchString, vocabulary);
    }

    /**
     * Gets all terms from a vocabulary, with label matching the searchString
     *
     * @param searchString String to search by
     * @return List of terms ordered by label
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAll(String searchString) {
        return termDao.findAll(searchString);
    }

    /**
     * Finds all terms which match the specified search string in the specified vocabulary and any vocabularies it
     * (transitively) imports.
     *
     * @param searchString Search string
     * @param vocabulary   Vocabulary whose terms should be returned
     * @return Matching terms
     */
    @Transactional(readOnly = true)
    public List<TermDto> findAllIncludingImported(String searchString, Vocabulary vocabulary) {
        return termDao.findAllIncludingImported(searchString, vocabulary);
    }

    /**
     * Finds all terms which are subterms of the specified term.
     *
     * @param parent Parent term
     * @return List of subterms
     */
    @Transactional(readOnly = true)
    public List<TermDto> findSubTerms(Term parent) {
        return termDao.findSubTerms(parent);
    }

    /**
     * Checks whether a term with the specified label exists in a vocabulary with the specified URI.
     *
     * @param label      Label to check
     * @param vocabulary Vocabulary in which terms will be searched
     * @param language   Language to check the existence in, optional. If not specified, any language is accepted
     * @return Whether term with {@code label} already exists in vocabulary
     */
    @Transactional(readOnly = true)
    public boolean existsInVocabulary(String label, Vocabulary vocabulary, String language) {
        return termDao.existsInVocabulary(label, vocabulary, language);
    }

    /**
     * Gets the identifier of a term with the specified label in a vocabulary with the specified URI.
     * <p>
     * Note that this method uses comparison ignoring case, so that two labels differing just in character case are
     * considered same here.
     *
     * @param label      Label to search by
     * @param vocabulary Vocabulary in which terms will be searched
     * @param language   Language tag of the label, optional. If not specified, any language is accepted
     * @return Identifier of matching term wrapped in an {@code Optional}, empty {@code Optional} if there is no such
     * term
     */
    @Transactional(readOnly = true)
    public Optional<URI> findIdentifierByLabel(String label, Vocabulary vocabulary, String language) {
        return termDao.findIdentifierByLabel(label, vocabulary, language);
    }

    /**
     * Gets the identifier of a vocabulary to which a term with the specified id belongs.
     *
     * @param termId Term identifier
     * @return Vocabulary identifier wrapped in {@code Optional}
     */
    @Transactional(readOnly = true)
    public Optional<URI> findTermVocabulary(URI termId) {
        return termDao.findTermVocabulary(termId);
    }

    /**
     * Checks that a term can be removed.
     * <p>
     * A term can be removed if:
     * <ul>
     *     <li>It does not have any children</li>
     *     <li>It does not occur in any resource and is not assigned to any resource</li>
     *     <li>Is not related to any other term via SKOS mapping properties</li>
     * </ul>
     *
     * @param instance The instance to be removed, not {@code null}
     * @throws AssetRemovalException If the specified term cannot be removed
     */
    @Override
    protected void preRemove(@Nonnull Term instance) {
        super.preRemove(instance);
        final List<TermOccurrences> occurrences = termOccurrenceService.getOccurrenceInfo(instance).stream()
                                                                       .filter(to -> !to.isSuggested()).toList();
        if (!occurrences.isEmpty()) {
            throw annotationsExistException(occurrences);
        }
        final Set<TermInfo> subTerms = instance.getSubTerms();
        if ((subTerms != null) && !subTerms.isEmpty()) {
            throw hasSubTermsException(subTerms);
        }
        if (instance.getProperties() != null) {
            Set<String> props = instance.getProperties().keySet();
            List<String> properties = props.stream().filter(s -> (s.startsWith(SKOS.NAMESPACE)) && !(
                    s.equalsIgnoreCase(SKOS.CHANGE_NOTE)
                            || s.equalsIgnoreCase(SKOS.EDITORIAL_NOTE)
                            || s.equalsIgnoreCase(SKOS.HISTORY_NOTE)
                            || s.equalsIgnoreCase(SKOS.NOTE))).collect(toList());
            if (!properties.isEmpty()) {
                throw hasSkosRelationships(properties);
            }
        }
    }

    private static TermItException annotationsExistException(List<TermOccurrences> ai) {
        final String resources = ai.stream().map(TermOccurrences::getResourceLabel).collect(
                joining(","));
        return new AssetRemovalException(
                "Cannot delete the term. It is used for annotating resources: " + resources,
                "error.term.remove.annotationsExist").addParameter("resources", resources);
    }

    private static TermItException hasSubTermsException(Set<TermInfo> subTerms) {
        final String children = subTerms.stream().map(t -> t.getUri().toString()).collect(joining(","));
        return new AssetRemovalException(
                "Cannot delete the term. It is a parent of other terms: " + children,
                "error.term.remove.hasSubTerms")
                .addParameter("subTerms", children);
    }

    private static TermItException hasSkosRelationships(List<String> properties) {
        final String propertiesStr = String.join(", ", properties);
        return new AssetRemovalException(
                "Cannot delete the term. It is linked to another term through properties "
                        + String.join(",", properties), "error.term.remove.skosRelationshipsExist")
                .addParameter("properties", propertiesStr);
    }

    @Override
    protected void postRemove(@Nonnull Term instance) {
        super.postRemove(instance);
        if (!instance.hasParentInSameVocabulary()) {
            final Vocabulary v = vocabularyService.findRequired(instance.getVocabulary());
            v.getGlossary().removeRootTerm(instance);
        }
        termOccurrenceService.removeAllOf(instance);
    }

    /**
     * Forcefully removes the specified term instance.
     * <p>
     * Extreme caution should be exercised when using this method as it does not perform any checks before removing the
     * specified instance.
     *
     * @param instance Term to remove
     */
    @Transactional
    public void forceRemove(@Nonnull Term instance) {
        super.preRemove(instance);
        termDao.remove(instance);
        postRemove(instance);
    }

    @Override
    public List<Snapshot> findSnapshots(Term asset) {
        return termDao.findSnapshots(asset);
    }

    @Override
    public Optional<Term> findVersionValidAt(Term asset, Instant at) {
        return termDao.findVersionValidAt(asset, at);
    }
}
