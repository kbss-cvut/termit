/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.InvalidTermStateException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.export.ExportConfig;
import cz.cvut.kbss.termit.service.export.VocabularyExporters;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Service for term-related business logic.
 */
@Service
public class TermService implements RudService<Term>, ChangeRecordProvider<Term> {

    private static final Logger LOG = LoggerFactory.getLogger(TermService.class);

    private final VocabularyExporters exporters;

    private final VocabularyService vocabularyService;

    private final VocabularyContextMapper vocabularyContextMapper;

    private final TermRepositoryService repositoryService;

    private final TextAnalysisService textAnalysisService;

    private final TermOccurrenceService termOccurrenceService;

    private final ChangeRecordService changeRecordService;

    private final CommentService commentService;

    private final LanguageService languageService;

    private final Configuration config;

    @Autowired
    public TermService(VocabularyExporters exporters, VocabularyService vocabularyService,
                       VocabularyContextMapper vocabularyContextMapper,
                       TermRepositoryService repositoryService, TextAnalysisService textAnalysisService,
                       TermOccurrenceService termOccurrenceService, ChangeRecordService changeRecordService,
                       CommentService commentService, LanguageService languageService, Configuration config) {
        this.exporters = exporters;
        this.vocabularyService = vocabularyService;
        this.vocabularyContextMapper = vocabularyContextMapper;
        this.repositoryService = repositoryService;
        this.textAnalysisService = textAnalysisService;
        this.termOccurrenceService = termOccurrenceService;
        this.changeRecordService = changeRecordService;
        this.commentService = commentService;
        this.languageService = languageService;
        this.config = config;
    }

    /**
     * Attempts to export glossary of the specified vocabulary according to the specified configuration.
     * <p>
     * If export into the specified media type is not supported, an empty {@link Optional} is returned.
     *
     * @param vocabulary Vocabulary to export
     * @param config     Expected media type of the export
     * @return Exported resource wrapped in an {@code Optional}
     */
    public Optional<TypeAwareResource> exportGlossary(Vocabulary vocabulary, ExportConfig config) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(config);
        return exporters.exportGlossary(vocabulary, config);
    }

    /**
     * Retrieves all terms from the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose terms will be returned. A reference is sufficient
     * @return Matching terms
     */
    public List<TermDto> findAll(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.findAll(vocabulary);
    }

    /**
     * Gets the total number of terms in the specified vocabulary.
     *
     * @param vocabulary Vocabulary reference
     * @return Number of terms in the specified vocabulary
     */
    public Integer getTermCount(Vocabulary vocabulary) {
        return vocabularyService.getTermCount(vocabulary);
    }

    /**
     * Retrieves all terms from the specified vocabulary and its imports (transitive).
     *
     * @param vocabulary Base vocabulary for the vocabulary import closure
     * @return Matching terms
     */
    public List<TermDto> findAllIncludingImported(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.findAllIncludingImported(vocabulary);
    }

    /**
     * Retrieves root terms (terms without parent) from the specified vocabulary.
     * <p>
     * The page specification parameter allows configuration of the number of results and their offset.
     *
     * @param vocabulary   Vocabulary whose terms will be returned
     * @param pageSpec     Paging specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms
     */
    public List<TermDto> findAllRoots(Vocabulary vocabulary, Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAllRoots(vocabulary, pageSpec, includeTerms);
    }

    /**
     * Retrieves root terms (terms without parent).
     * <p>
     * The page specification parameter allows configuration of the number of results and their offset.
     * <p>
     * Terms with a label in the instance language are prepended.
     *
     * @param pageSpec     Paging specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms
     */
    public List<TermDto> findAllRoots(Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAllRoots(pageSpec, includeTerms);
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
    public List<TermDto> findAllRootsIncludingImported(Vocabulary vocabulary, Pageable pageSpec,
                                                       Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        return repositoryService.findAllRootsIncludingImported(vocabulary, pageSpec, includeTerms);
    }

    /**
     * Finds all terms which match the specified search string in the specified vocabulary.
     *
     * @param searchString Search string
     * @param vocabulary   Vocabulary whose terms should be returned
     * @return Matching terms
     */
    public List<TermDto> findAll(String searchString, Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(searchString);
        return repositoryService.findAll(searchString, vocabulary);
    }

    /**
     * Finds all terms label of which is matching the searchString.
     *
     * @param searchString string to search the label by
     * @return Matching terms
     */
    public List<TermDto> findAll(String searchString) {
        Objects.requireNonNull(searchString);
        return repositoryService.findAll(searchString);
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
        Objects.requireNonNull(searchString);
        Objects.requireNonNull(vocabulary);
        return repositoryService.findAllIncludingImported(searchString, vocabulary);
    }

    /**
     * Gets vocabulary with the specified identifier.
     *
     * @param id Vocabulary identifier
     * @return Matching vocabulary
     * @throws NotFoundException When vocabulary with the specified identifier does not exist
     */
    @PostAuthorize("@vocabularyAuthorizationService.canRead(returnObject)")
    public Vocabulary findVocabularyRequired(URI id) {
        return vocabularyService.findRequired(id);
    }

    /**
     * Gets a reference to the vocabulary with the specified identifier.
     *
     * @param id Vocabulary identifier
     * @return Matching vocabulary reference
     * @throws NotFoundException When vocabulary with the specified identifier does not exist
     */
    @PostAuthorize("@vocabularyAuthorizationService.canRead(returnObject)")
    public Vocabulary getVocabularyReference(URI id) {
        return vocabularyService.getReference(id);
    }

    /**
     * Gets a term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term wrapped in an {@code Optional}
     */
    @PostAuthorize("@termAuthorizationService.canRead(returnObject)")
    public Optional<Term> find(URI id) {
        final Optional<Term> result = repositoryService.find(id);
        result.ifPresent(TermService::consolidateAttributes);
        return result;
    }

    private static void consolidateAttributes(Term term) {
        term.consolidateInferred();
        term.consolidateParents();
    }

    /**
     * Gets a term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term
     * @throws NotFoundException When no matching term is found
     */
    @PostAuthorize("@termAuthorizationService.canRead(returnObject)")
    public Term findRequired(URI id) {
        final Term result = repositoryService.findRequired(id);
        assert result != null;
        consolidateAttributes(result);
        return result;
    }

    /**
     * Gets a reference to a Term with the specified identifier.
     * <p>
     * Note that this method is not protected by ACL-based authorization and should thus not be used without some other
     * type of authorization.
     *
     * @param id Term identifier
     * @return Matching Term reference wrapped in an {@code Optional}
     */
    public Term getReference(URI id) {
        return repositoryService.getReference(id);
    }

    /**
     * Gets child terms of the specified parent term.
     *
     * @param parent Parent term whose children should be loaded
     * @return List of child terms
     */
    @PreAuthorize("@termAuthorizationService.canRead(#parent)")
    public List<Term> findSubTerms(Term parent) {
        Objects.requireNonNull(parent);
        return parent.getSubTerms() == null ? Collections.emptyList() :
               parent.getSubTerms().stream().map(u -> repositoryService.find(u.getUri()).orElseThrow(
                             () -> new NotFoundException(
                                     "Child of term " + parent + " with id " + u.getUri() + " not found!")))
                     .sorted(Comparator.comparing((Term t) -> t.getLabel().get(config.getPersistence().getLanguage()),
                                                  Comparator.nullsLast(Comparator.naturalOrder())))
                     .collect(Collectors.toList());
    }

    /**
     * Gets aggregated info about occurrences of the specified Term.
     *
     * @param term Term whose occurrences to retrieve
     * @return List of term occurrences describing instances
     */
    @PreAuthorize("@termAuthorizationService.canRead(#term)")
    public List<TermOccurrences> getOccurrenceInfo(Term term) {
        Objects.requireNonNull(term);
        return repositoryService.getOccurrenceInfo(term);
    }

    /**
     * Checks whether a term with the specified label already exists in the specified vocabulary.
     *
     * @param termLabel  Label to search for
     * @param vocabulary Vocabulary in which to search
     * @param language   Language to check existence in.
     * @return Whether a matching label was found
     */
    public boolean existsInVocabulary(String termLabel, Vocabulary vocabulary, String language) {
        Objects.requireNonNull(termLabel);
        Objects.requireNonNull(vocabulary);
        return repositoryService.existsInVocabulary(termLabel, vocabulary, language);
    }

    /**
     * Persists the specified term as a root term in the specified vocabulary's glossary.
     *
     * @param term  Term to persist
     * @param owner Vocabulary to add the term to
     */
    @PreAuthorize("@termAuthorizationService.canCreateIn(#owner)")
    public void persistRoot(Term term, Vocabulary owner) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(owner);
        languageService.getInitialTermState().ifPresent(is -> term.setState(is.getUri()));
        repositoryService.addRootTermToVocabulary(term, owner);
        if (!config.getTextAnalysis().isDisableVocabularyAnalysisOnTermEdit()) {
            analyzeTermDefinition(term, owner.getUri());
            vocabularyService.runTextAnalysisOnAllTerms(owner);
        }
    }

    /**
     * Persists the specified term as a child of the specified parent term.
     *
     * @param child  The child to persist
     * @param parent Existing parent term
     */
    @PreAuthorize("@termAuthorizationService.canCreateChild(#parent)")
    public void persistChild(Term child, Term parent) {
        Objects.requireNonNull(child);
        Objects.requireNonNull(parent);
        languageService.getInitialTermState().ifPresent(is -> child.setState(is.getUri()));
        repositoryService.addChildTerm(child, parent);
        if (!config.getTextAnalysis().isDisableVocabularyAnalysisOnTermEdit()) {
            analyzeTermDefinition(child, parent.getVocabulary());
            vocabularyService.runTextAnalysisOnAllTerms(findVocabularyRequired(parent.getVocabulary()));
        }
    }

    /**
     * Updates the specified term.
     *
     * @param term Term update data
     * @return The updated term
     */
    @PreAuthorize("@termAuthorizationService.canModify(#term)")
    public Term update(Term term) {
        Objects.requireNonNull(term);
        final Term original = repositoryService.findRequired(term.getUri());
        languageService.verifyStateExists(term.getState());
        checkForInvalidTerminalStateAssignment(original, term.getState());
        // Ensure the change is merged into the repo before analyzing other terms
        final Term result = repositoryService.update(term);
        if (!Objects.equals(original.getDefinition(), term.getDefinition()) && !config.getTextAnalysis().isDisableVocabularyAnalysisOnTermEdit()) {
            analyzeTermDefinition(term, original.getVocabulary());
        }
        if (!Objects.equals(original.getLabel(), term.getLabel()) && !config.getTextAnalysis().isDisableVocabularyAnalysisOnTermEdit()) {
            vocabularyService.runTextAnalysisOnAllTerms(getVocabularyReference(original.getVocabulary()));
        }
        return result;
    }

    /**
     * Removes the specified term.
     *
     * @param term Term to remove
     */
    @PreAuthorize("@termAuthorizationService.canRemove(#term)")
    public void remove(@NonNull Term term) {
        Objects.requireNonNull(term);
        repositoryService.remove(term);
    }

    /**
     * Executes text analysis on the specified term's definition.
     * <p>
     * A vocabulary with the specified identifier is used as base for the text analysis (its terms are searched for
     * during the analysis).
     *
     * @param term          Term to analyze
     * @param vocabularyIri Identifier of the vocabulary used for analysis
     */
    @PreAuthorize("@termAuthorizationService.canModify(#term)")
    public void analyzeTermDefinition(AbstractTerm term, URI vocabularyIri) {
        Objects.requireNonNull(term);
        if (term.getDefinition() == null || term.getDefinition().isEmpty()) {
            return;
        }
        LOG.debug("Analyzing definition of term {}.", term);
        textAnalysisService.analyzeTermDefinition(term, vocabularyContextMapper.getVocabularyContext(vocabularyIri));
    }

    /**
     * Gets occurrences of terms which appear in the specified term's definition.
     *
     * @param instance Term in whose definition to search for related terms
     * @return List of term occurrences in the specified term's definition
     */
    public List<TermOccurrence> getDefinitionallyRelatedTargeting(Term instance) {
        return repositoryService.getDefinitionallyRelatedTargeting(instance);
    }

    /**
     * Gets occurrences of the specified term in other terms' definitions.
     *
     * @param instance Term whose definitional occurrences to search for
     * @return List of definitional occurrences of the specified term
     */
    public List<TermOccurrence> getDefinitionallyRelatedOf(Term instance) {
        return repositoryService.getDefinitionallyRelatedOf(instance);
    }

    /**
     * Sets the definition source of the specified term.
     * <p>
     * It removes the previously existing definition source if there was any.
     *
     * @param term             Term whose definition source is being specified
     * @param definitionSource Definition source representation
     */
    @Transactional
    @PreAuthorize("@termAuthorizationService.canModify(#term)")
    public void setTermDefinitionSource(Term term, TermDefinitionSource definitionSource) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(definitionSource);
        definitionSource.setTerm(term.getUri());
        if (term.getDefinitionSource() != null) {
            termOccurrenceService.remove(term.getDefinitionSource().getUri());
        }
        termOccurrenceService.persist(definitionSource);
    }

    /**
     * Removes the definition source of the specified term.
     * <p>
     * This involves deleting the {@link TermDefinitionSource} instance representing the definition source from the
     * repository.
     * <p>
     * If the specified term has no definition source, nothing happens.
     *
     * @param term Term whose definition to remove
     */
    @Transactional
    @PreAuthorize("@termAuthorizationService.canModify(#term)")
    public void removeTermDefinitionSource(Term term) {
        Objects.requireNonNull(term);
        if (term.getDefinitionSource() != null) {
            termOccurrenceService.remove(term.getDefinitionSource().getUri());
        }
    }

    /**
     * Updates the specified term's state to the specified new value.
     *
     * @param term  Term to update
     * @param state New state
     */
    @Transactional
    @PreAuthorize("@termAuthorizationService.canModify(#term)")
    public void setState(Term term, URI state) {
        languageService.verifyStateExists(state);
        checkForInvalidTerminalStateAssignment(term, state);
        repositoryService.setState(term, state);
    }

    private void checkForInvalidTerminalStateAssignment(Term term, URI state) {
        final List<RdfsResource> states = languageService.getTermStates();
        final Predicate<URI> isStateTerminal = (URI s) -> states.stream().filter(r -> r.getUri().equals(s)).findFirst()
                                                                .map(r -> r.hasType(
                                                                        cz.cvut.kbss.termit.util.Vocabulary.s_c_koncovy_stav_pojmu))
                                                                .orElse(false);
        if (!isStateTerminal.test(state)) {
            return;
        }
        if (Utils.emptyIfNull(term.getSubTerms()).stream()
                 .anyMatch(Predicate.not(ti -> isStateTerminal.test(ti.getState())))) {
            throw new InvalidTermStateException(
                    "Cannot set state of term " + term + " to terminal when at least one of its sub-terms is not in terminal state.",
                    "error.term.state.terminal.liveChildren");
        }
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Term term) {
        Objects.requireNonNull(term);
        return changeRecordService.getChanges(term);
    }

    /**
     * Gets comments related to the specified term created in the specified time interval.
     *
     * @param term Term to get comments for
     * @param from Retrieval interval start
     * @param to   Retrieval interval end
     * @return List of comments
     */
    public List<Comment> getComments(Term term, Instant from, Instant to) {
        return commentService.findAll(term, from, to);
    }

    /**
     * Adds the specified comment to the specified target term.
     *
     * @param comment Comment to add (create)
     * @param target  Term to which the comment pertains
     */
    public void addComment(Comment comment, Term target) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(target);
        commentService.addToAsset(comment, target);
    }

    public List<Snapshot> findSnapshots(Term asset) {
        return repositoryService.findSnapshots(asset);
    }

    public Term findVersionValidAt(Term asset, Instant at) {
        return repositoryService.findVersionValidAt(asset, at).map(t -> {
            consolidateAttributes(t);
            return t;
        }).orElseThrow(() -> new NotFoundException("No version valid at " + at + " exists."));
    }
}
