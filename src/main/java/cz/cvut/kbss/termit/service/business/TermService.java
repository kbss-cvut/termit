package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.export.VocabularyExporters;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(TermService.class);

    private final VocabularyExporters exporters;

    private final VocabularyService vocabularyService;

    private final TermRepositoryService repositoryService;

    private final TextAnalysisService textAnalysisService;

    private final TermOccurrenceService termOccurrenceService;

    private final ChangeRecordService changeRecordService;

    private final CommentService commentService;

    private final Configuration config;

    @Autowired
    public TermService(VocabularyExporters exporters, VocabularyService vocabularyService,
                       TermRepositoryService repositoryService, TextAnalysisService textAnalysisService,
                       TermOccurrenceService termOccurrenceService, ChangeRecordService changeRecordService,
                       CommentService commentService, Configuration config) {
        this.exporters = exporters;
        this.vocabularyService = vocabularyService;
        this.repositoryService = repositoryService;
        this.textAnalysisService = textAnalysisService;
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
        return exporters.exportGlossary(vocabulary, mediaType);
    }

    /**
     * Attempts to export glossary terms including references to external terms from the specified vocabulary as the specified media type.
     * <p>
     * If export into the specified media type is not supported, an empty {@link Optional} is returned.
     *
     * @param vocabulary Vocabulary to export
     * @param mediaType  Expected media type of the export
     * @return Exported resource wrapped in an {@code Optional}
     * @see cz.cvut.kbss.termit.service.export.VocabularyExporter
     */
    public Optional<TypeAwareResource> exportGlossaryWithReferences(Vocabulary vocabulary,
                                                                    Collection<String> properties, String mediaType) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(properties);
        return exporters.exportGlossaryWithReferences(vocabulary, properties, mediaType);
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
    public Vocabulary getRequiredVocabularyReference(URI id) {
        return vocabularyService.getRequiredReference(id);
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
                      .sorted(Comparator.comparing((Term t) -> t.getLabel().get(config.getPersistence().getLanguage())))
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
        analyzeTermDefinition(term, owner.getUri());
        vocabularyService.runTextAnalysisOnAllTerms(owner);
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
        analyzeTermDefinition(child, parent.getVocabulary());
        vocabularyService.runTextAnalysisOnAllTerms(getRequiredVocabularyReference(parent.getVocabulary()));
    }

    /**
     * Updates the specified term.
     *
     * @param term Term update data
     * @return The updated term
     */
    public Term update(Term term) {
        Objects.requireNonNull(term);
        final Term original = repositoryService.findRequired(term.getUri());
        if (!Objects.equals(original.getDefinition(), term.getDefinition())) {
            analyzeTermDefinition(term, term.getVocabulary());
        }
        final Term result = repositoryService.update(term);
        // Ensure the change is merged into the repo before analyzing other terms
        if (!Objects.equals(original.getLabel(), term.getLabel())) {
            vocabularyService.runTextAnalysisOnAllTerms(getRequiredVocabularyReference(original.getVocabulary()));
        }
        return result;
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
     * Executes text analysis on the specified term's definition.
     * <p>
     * A vocabulary with the specified identifier is used as base for the text analysis (its terms are searched for
     * during the analysis).
     *
     * @param term              Term to analyze
     * @param vocabularyContext Identifier of the vocabulary used for analysis
     */
    public void analyzeTermDefinition(Term term, URI vocabularyContext) {
        Objects.requireNonNull(term);
        if (term.getDefinition().isEmpty()) {
            return;
        }
        LOG.debug("Analyzing definition of term {}.", term);
        textAnalysisService.analyzeTermDefinition(term, vocabularyContext);
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
