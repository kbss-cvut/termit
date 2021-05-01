package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.export.VocabularyExporters;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for term-related business logic.
 */
@Service
public class TermService implements RudService<Term>, ChangeRecordProvider<Term> {

    private final VocabularyExporters exporters;

    private final VocabularyService vocabularyService;

    private final TermRepositoryService repositoryService;

    private final TermOccurrenceService termOccurrenceService;

    private final ChangeRecordService changeRecordService;

    private final CommentService commentService;

    private final Configuration config;

    @Autowired
    public TermService(VocabularyExporters exporters, VocabularyService vocabularyService,
                       TermRepositoryService repositoryService,
                       TermOccurrenceService termOccurrenceService, ChangeRecordService changeRecordService,
                       CommentService commentService, Configuration config) {
        this.exporters = exporters;
        this.vocabularyService = vocabularyService;
        this.repositoryService = repositoryService;
        this.termOccurrenceService = termOccurrenceService;
        this.changeRecordService = changeRecordService;
        this.commentService = commentService;
        this.config = config;
    }

    /**
     * Attempts to export glossary terms from the specified vocabulary as the specified media type.
     * <p>
     * If export into the specified media type is not supported, an empty {@link Optional} is returned.
     *
     * @param vocabulary Vocabulary to export
     * @param mediaType  Expected media type of the export
     * @return Exported resource wrapped in an {@code Optional}
     */
    public Optional<TypeAwareResource> exportGlossary(Vocabulary vocabulary, String mediaType) {
        Objects.requireNonNull(vocabulary);
        return exporters.exportVocabularyGlossary(vocabulary, mediaType);
    }

    /**
     * Retrieves all terms from the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose terms will be returned
     * @return Matching terms
     */
    public List<Term> findAll(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.findAll(vocabulary);
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
     * Finds out whether the given vocabulary contains any terms or not.
     *
     * @param vocabulary vocabulary under consideration
     * @return true if the vocabulary contains no terms, false otherwise
     */
    public boolean isEmpty(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.isEmpty(vocabulary);
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
    public Vocabulary findVocabularyRequired(URI id) {
        Objects.requireNonNull(id);
        return vocabularyService.find(id)
                                .orElseThrow(() -> NotFoundException.create(Vocabulary.class.getSimpleName(), id));
    }

    /**
     * Gets a term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term wrapped in an {@code Optional}
     */
    public Optional<Term> find(URI id) {
        return repositoryService.find(id);
    }

    /**
     * Gets a term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term
     * @throws NotFoundException When no matching term is found
     */
    public Term findRequired(URI id) {
        return repositoryService.findRequired(id);
    }

    /**
     * Gets a reference to a Term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching Term reference wrapped in an {@code Optional}
     */
    public Optional<Term> getReference(URI id) {
        return repositoryService.getReference(id);
    }

    /**
     * Gets a reference to a Term with the specified identifier.
     *
     * @param id Term identifier
     * @return Matching term reference
     * @throws NotFoundException When no matching term is found
     */
    public Term getRequiredReference(URI id) {
        return repositoryService.getRequiredReference(id);
    }

    /**
     * Gets child terms of the specified parent term.
     *
     * @param parent Parent term whose children should be loaded
     * @return List of child terms
     */
    public List<Term> findSubTerms(Term parent) {
        Objects.requireNonNull(parent);
        return parent.getSubTerms() == null ? Collections.emptyList() :
               parent.getSubTerms().stream().map(u -> repositoryService.find(u.getUri()).orElseThrow(
                       () -> new NotFoundException(
                               "Child of term " + parent + " with id " + u.getUri() + " not found!")))
                     .sorted(Comparator.comparing((Term t) -> t.getLabel().get(config.get(ConfigParam.LANGUAGE))))
                     .collect(Collectors.toList());
    }

    /**
     * Gets aggregated info about assignments and occurrences of the specified Term.
     *
     * @param term Term whose assignments and occurrences to retrieve
     * @return List of term assignment describing instances
     */
    public List<TermAssignments> getAssignmentInfo(Term term) {
        Objects.requireNonNull(term);
        return repositoryService.getAssignmentsInfo(term);
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
    public void persistRoot(Term term, Vocabulary owner) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(owner);
        repositoryService.addRootTermToVocabulary(term, owner);
    }

    /**
     * Persists the specified term as a child of the specified parent term.
     *
     * @param child  The child to persist
     * @param parent Existing parent term
     */
    public void persistChild(Term child, Term parent) {
        Objects.requireNonNull(child);
        Objects.requireNonNull(parent);
        repositoryService.addChildTerm(child, parent);
    }

    /**
     * Updates the specified term.
     *
     * @param term Term update data
     * @return The updated term
     */
    public Term update(Term term) {
        Objects.requireNonNull(term);
        return repositoryService.update(term);
    }

    /**
     * Removes the specified term.
     *
     * @param term Term to remove
     */
    public void remove(Term term) {
        Objects.requireNonNull(term);
        repositoryService.remove(term);
    }

    /**
     * Sets the definition source of the specified term.
     *
     * @param term             Term whose definition source is being specified
     * @param definitionSource Definition source representation
     */
    @Transactional
    public void setTermDefinitionSource(Term term, TermDefinitionSource definitionSource) {
        Objects.requireNonNull(term);
        Objects.requireNonNull(definitionSource);
        definitionSource.setTerm(term.getUri());
        final Term existingTerm = repositoryService.findRequired(term.getUri());
        if (existingTerm.getDefinitionSource() != null) {
            termOccurrenceService.removeOccurrence(existingTerm.getDefinitionSource());
        }
        termOccurrenceService.persistOccurrence(definitionSource);
    }

    /**
     * Gets a reference to a Term occurrence with the specified identifier.
     *
     * @param id Term occurrence identifier
     * @return Matching Term occurrence reference
     */
    public TermOccurrence getRequiredOccurrenceReference(URI id) {
        return termOccurrenceService.getRequiredReference(id);
    }

    public void approveOccurrence(URI identifier) {
        termOccurrenceService.approveOccurrence(identifier);
    }

    public void removeOccurrence(TermOccurrence occurrence) {
        termOccurrenceService.removeOccurrence(occurrence);
    }

    /**
     * Gets unused terms (in annotations/occurences).
     *
     * @return List of terms
     */
    public List<URI> getUnusedTermsInVocabulary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return repositoryService.getUnusedTermsInVocabulary(vocabulary);
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Term term) {
        Objects.requireNonNull(term);
        return changeRecordService.getChanges(term);
    }

    /**
     * Gets comments related to the specified term.
     *
     * @param term Term to get comments for
     * @return List of comments
     */
    public List<Comment> getComments(Term term) {
        return commentService.findAll(term);
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
}
