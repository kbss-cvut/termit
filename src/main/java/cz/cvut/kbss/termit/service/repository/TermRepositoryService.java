/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.TermRemovalException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.persistence.dao.TermAssignmentDao;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.term.AssertedInferredValueDifferentiator;
import cz.cvut.kbss.termit.service.term.OrphanedInverseTermRelationshipRemover;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import org.apache.jena.vocabulary.SKOS;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Validator;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Service
public class TermRepositoryService extends BaseAssetRepositoryService<Term> {

    private final IdentifierResolver idResolver;

    private final Configuration config;

    private final TermDao termDao;

    private final OrphanedInverseTermRelationshipRemover orphanedRelationshipRemover;

    private final TermAssignmentDao termAssignmentDao;

    private final VocabularyRepositoryService vocabularyService;

    public TermRepositoryService(Validator validator, IdentifierResolver idResolver,
                                 Configuration config, TermDao termDao,
                                 OrphanedInverseTermRelationshipRemover orphanedRelationshipRemover, TermAssignmentDao termAssignmentDao,
                                 VocabularyRepositoryService vocabularyService) {
        super(validator);
        this.idResolver = idResolver;
        this.config = config;
        this.termDao = termDao;
        this.orphanedRelationshipRemover = orphanedRelationshipRemover;
        this.termAssignmentDao = termAssignmentDao;
        this.vocabularyService = vocabularyService;
    }

    @Override
    protected AssetDao<Term> getPrimaryDao() {
        return termDao;
    }

    @Override
    public Optional<Term> find(URI id) {
        final Optional<Term> result = super.find(id);
        return result.map(t -> {
            t.consolidateInferred();
            return t;
        });
    }

    @Override
    public void persist(Term instance) {
        throw new UnsupportedOperationException(
            "Persisting term by itself is not supported. It has to be connected to a vocabulary or a parent term.");
    }

    @Override
    protected void preUpdate(Term instance) {
        super.preUpdate(instance);
        // Existence check is done as part of super.preUpdate
        final Term original = termDao.find(instance.getUri()).get();
        termDao.detach(original);
        final AssertedInferredValueDifferentiator differentiator = new AssertedInferredValueDifferentiator();
        differentiator.differentiateRelatedTerms(instance, original);
        differentiator.differentiateRelatedMatchTerms(instance, original);
        differentiator.differentiateExactMatchTerms(instance, original);
        orphanedRelationshipRemover.removeOrphanedInverseTermRelationships(instance, original);
    }

    @Override
    protected void postUpdate(Term instance) {
        final Vocabulary vocabulary =
            vocabularyService.getRequiredReference(instance.getVocabulary());
        if (instance.hasParentInSameVocabulary()) {
            vocabulary.getGlossary().removeRootTerm(instance);
        } else {
            vocabulary.getGlossary().addRootTerm(instance);
        }
    }

    @Transactional
    public void addRootTermToVocabulary(Term instance, Vocabulary vocabulary) {
        validate(instance);

        if (instance.getUri() == null) {
            instance.setUri(generateIdentifier(vocabulary.getUri(), instance.getLabel()));
        }
        verifyIdentifierUnique(instance);
        instance.setGlossary(vocabulary.getGlossary().getUri());
        addTermAsRootToGlossary(instance, vocabulary.getUri());
        termDao.persist(instance, vocabulary);
    }

    private URI generateIdentifier(URI vocabularyUri, MultilingualString multilingualString) {
        return idResolver.generateDerivedIdentifier(vocabularyUri, config.getNamespace().getTerm().getSeparator(),
            multilingualString.get(config.getPersistence().getLanguage()));
    }

    private void addTermAsRootToGlossary(Term instance, URI vocabularyIri) {
        // Load vocabulary so that it is managed and changes to it (resp. the glossary) are persisted on commit
        final Vocabulary toUpdate = vocabularyService.getRequiredReference(vocabularyIri);
        instance.setGlossary(toUpdate.getGlossary().getUri());
        toUpdate.getGlossary().addRootTerm(instance);
    }

    @Transactional
    public void addChildTerm(Term instance, Term parentTerm) {
        validate(instance);
        final URI vocabularyIri =
            instance.getVocabulary() != null ? instance.getVocabulary() : parentTerm.getVocabulary();
        if (instance.getUri() == null) {
            instance.setUri(generateIdentifier(vocabularyIri, instance.getLabel()));
        }
        verifyIdentifierUnique(instance);

        instance.addParentTerm(parentTerm);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(vocabularyIri);
        instance.setGlossary(vocabulary.getGlossary().getUri());
        if (!instance.hasParentInSameVocabulary()) {
            addTermAsRootToGlossary(instance, vocabularyIri);
        }

        termDao.persist(instance, vocabulary);
    }

    /**
     * Gets all terms from a vocabulary, regardless of their position in the term hierarchy.
     * <p>
     * This returns all terms contained in a vocabulary's glossary.
     *
     * @param vocabulary Vocabulary whose terms should be returned
     * @return List of terms ordered by label
     */
    public List<Term> findAll(Vocabulary vocabulary) {
        return termDao.findAll(vocabulary);
    }

    /**
     * Checks whether the vocabulary contains any terms or not.
     *
     * @param vocabulary Base vocabulary for the vocabulary import closure
     * @return true if the vocabulary is empty
     */
    public boolean isEmpty(Vocabulary vocabulary) {
        return termDao.isEmpty(vocabulary);
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
    public List<TermDto> findAllIncludingImported(Vocabulary vocabulary) {
        return termDao.findAllIncludingImported(vocabulary);
    }

    /**
     * Finds all root terms (terms without parent term) in the specified vocabulary.
     *
     * @param vocabulary   Vocabulary whose terms should be returned
     * @param pageSpec     Page specifying result number and position
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching root terms
     * @see #findAllRootsIncludingImported(Vocabulary, Pageable, Collection)
     */
    public List<TermDto> findAllRoots(Vocabulary vocabulary, Pageable pageSpec,
                                      Collection<URI> includeTerms) {
        return termDao.findAllRoots(vocabulary, pageSpec, includeTerms);
    }

    /**
     * Finds all root terms (terms without parent term).
     *
     * @param pageSpec     Page specifying result number and position
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching root terms
     * @see #findAllRootsIncludingImported(Vocabulary, Pageable, Collection)
     */
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
     *
     * @param vocabulary   Base vocabulary for the vocabulary import closure
     * @param pageSpec     Page specifying result number and position
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching root terms
     * @see #findAllRoots(Vocabulary, Pageable, Collection)
     */
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
    public List<TermDto> findAll(String searchString, Vocabulary vocabulary) {
        return termDao.findAll(searchString, vocabulary);
    }

    /**
     * Gets all terms from a vocabulary, with label matching the searchString
     *
     * @param searchString String to search by
     * @return List of terms ordered by label
     */
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
    public List<TermDto> findAllIncludingImported(String searchString, Vocabulary vocabulary) {
        return termDao.findAllIncludingImported(searchString, vocabulary);
    }

    /**
     * Checks whether a term with the specified label exists in a vocabulary with the specified URI.
     *
     * @param label      Label to check
     * @param vocabulary Vocabulary in which terms will be searched
     * @param language   Language to check the existence in
     * @return Whether term with {@code label} already exists in vocabulary
     */
    public boolean existsInVocabulary(String label, Vocabulary vocabulary, String language) {
        return termDao.existsInVocabulary(label, vocabulary, language);
    }

    /**
     * Retrieves aggregated information about the specified Term's assignments to and occurrences in {@link
     * cz.cvut.kbss.termit.model.resource.Resource}s.
     *
     * @param instance Term whose assignment/occurrence data should be retrieved
     * @return Aggregated Term assignment/occurrence data
     */
    public List<TermAssignments> getAssignmentsInfo(Term instance) {
        return termAssignmentDao.getAssignmentInfo(instance);
    }

    /**
     * Gets all unused (unassigned to, neither occuring in a resource) terms in the given vocabulary
     *
     * @param vocabulary - IRI of the vocabulary in which the terms are
     * @return List of definitionally related terms of the specified term
     */
    public List<URI> getUnusedTermsInVocabulary(Vocabulary vocabulary) {
        throw new UnsupportedOperationException("This method is disabled, not working correctly.");
    }

    /**
     * Removes a term if it: - does not have children, - is not related to any resource, - is not related to any term
     * occurrences.
     *
     * @param instance the term to be deleted
     */
    public void remove(Term instance) {

        final List<TermAssignments> ai = this.getAssignmentsInfo(instance);

        if (!ai.isEmpty()) {
            throw new TermRemovalException(
                "Cannot delete the term. It is used for annotating resources : " +
                    ai.stream().map(TermAssignments::getResourceLabel).collect(
                        joining(",")));
        }

        final Set<TermInfo> subTerms = instance.getSubTerms();
        if ((subTerms != null) && !subTerms.isEmpty()) {
            throw new TermRemovalException(
                "Cannot delete the term. It is a parent of other terms : " + subTerms
                    .stream().map(t -> t.getUri().toString())
                    .collect(joining(",")));
        }

        if (instance.getProperties() != null) {
            Set<String> props = instance.getProperties().keySet();
            List<String> properties = props.stream().filter(s -> (s.startsWith(SKOS.getURI())) && !(
                s.equalsIgnoreCase(SKOS.changeNote.toString())
                    || s.equalsIgnoreCase(SKOS.editorialNote.toString())
                    || s.equalsIgnoreCase(SKOS.historyNote.toString())
                    || s.equalsIgnoreCase(SKOS.example.toString())
                    || s.equalsIgnoreCase(SKOS.note.toString())
                    || s.equalsIgnoreCase(SKOS.scopeNote.toString())
                    || s.equalsIgnoreCase(SKOS.notation.toString()))).collect(toList());
            if (!properties.isEmpty()) {
                throw new TermRemovalException(
                    "Cannot delete the term. It is linked to another term through properties "
                        + String.join(",", properties));
            }
        }

        super.remove(instance);
    }
}
