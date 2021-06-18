package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.util.*;
import cz.cvut.kbss.termit.util.Constants.Excel;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import cz.cvut.kbss.termit.util.Constants.Turtle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/")
public class TermController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(TermController.class);

    private final TermService termService;

    @Autowired
    public TermController(IdentifierResolver idResolver, Configuration config, TermService termService) {
        super(idResolver, config);
        this.termService = termService;
    }

    private URI getVocabularyUri(Optional<String> namespace, String fragment) {
        return resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
    }

    /**
     * Get all terms from vocabulary with the specified identification.
     * <p>
     * This method also allows to export the terms into CSV or Excel by using HTTP content type negotiation or filter
     * terms by a search string.
     *
     * @param vocabularyIdFragment Vocabulary name
     * @param namespace            Vocabulary namespace. Optional
     * @param searchString         String to filter term labels by. Optional
     * @return List of terms of the specific vocabulary
     */
    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms",
                produces = {MediaType.APPLICATION_JSON_VALUE,
                            JsonLd.MEDIA_TYPE,
                            CsvUtils.MEDIA_TYPE,
                            Excel.MEDIA_TYPE,
                            Turtle.MEDIA_TYPE})
    public ResponseEntity<?> getAll(@PathVariable String vocabularyIdFragment,
                                    @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
                                    @RequestParam(name = "searchString", required = false) String searchString,
                                    @RequestParam(name = "includeImported", required = false) boolean includeImported,
                                    @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptType) {
        final URI vocabularyUri = getVocabularyUri(namespace, vocabularyIdFragment);
        final Vocabulary vocabulary = getVocabulary(vocabularyUri);
        if (searchString != null) {
            return ResponseEntity.ok(includeImported ?
                    termService.findAllIncludingImported(searchString, vocabulary) :
                    termService.findAll(searchString, vocabulary));
        }
        final Optional<ResponseEntity<?>> export = exportTerms(vocabulary, vocabularyIdFragment, acceptType);
        return export.orElse(ResponseEntity
                .ok(includeImported ? termService.findAllIncludingImported(vocabulary) :
                        termService.findAll(vocabulary)));
    }

    private Optional<ResponseEntity<?>> exportTerms(Vocabulary vocabulary, String fileName,
                                                    String mediaType) {
        final Optional<TypeAwareResource> content = termService.exportGlossary(vocabulary, mediaType);
        return content.map(r -> {
            try {
                return ResponseEntity.ok()
                                     .contentLength(r.contentLength())
                                     .contentType(MediaType.parseMediaType(mediaType))
                                     .header(HttpHeaders.CONTENT_DISPOSITION,
                                             "attachment; filename=\"" + fileName +
                                                     r.getFileExtension().orElse("") + "\"")
                                     .body(r);
            } catch (IOException e) {
                throw new TermItException("Unable to export terms.", e);
            }
        });
    }

    /**
     * Performs simple vocabulary-based checks. If {@code prefLabel} is passed in, it checks whether a term with the
     * given pref label exists in the given vocabulary for the given language. If not, this method returns the number of
     * terms in the specified vocabulary in a special response header.
     *
     * @param vocabularyIdFragment vocabulary id fragment
     * @param namespace            vocabulary namespace
     * @param prefLabel            the label to check, optional
     * @param language             language to check existence in, optional
     * @return OK response when term exists and label check was performed, Not Found otherwise. If no label to check was
     * passed, an empty OK response with header containing total number of terms is returned
     */
    @PreAuthorize("permitAll()")
    @RequestMapping(method = RequestMethod.HEAD, value = "/vocabularies/{vocabularyIdFragment}/terms")
    public ResponseEntity<Void> checkTerms(@PathVariable String vocabularyIdFragment,
                                           @RequestParam(name = QueryParams.NAMESPACE,
                                                         required = false) Optional<String> namespace,
                                           @RequestParam(name = "prefLabel", required = false) String prefLabel,
                                           @RequestParam(name = "language", required = false) String language) {
        final URI vocabularyUri = getVocabularyUri(namespace, vocabularyIdFragment);
        final Vocabulary vocabulary = termService.getRequiredVocabularyReference(vocabularyUri);
        if (prefLabel != null) {
            final boolean exists = termService.existsInVocabulary(prefLabel, vocabulary, language);
            return new ResponseEntity<>(exists ? HttpStatus.OK : HttpStatus.NOT_FOUND);
        } else {
            final Integer count = termService.getTermCount(vocabulary);
            return ResponseEntity.ok().header(Constants.X_TOTAL_COUNT_HEADER, count.toString()).build();
        }
    }

    private Vocabulary getVocabulary(URI vocabularyUri) {
        return termService.findVocabularyRequired(vocabularyUri);
    }

    /**
     * Get all root terms from vocabulary with the specified identification.
     * <p>
     * Optionally, the terms can be filtered by the specified search string, so that only roots with descendants with
     * label matching the specified string are returned.
     *
     * @param vocabularyIdFragment Vocabulary name
     * @param namespace            Vocabulary namespace. Optional
     * @param pageSize             Limit the number of elements in the returned page. Optional
     * @param pageNo               Number of the page to return. Optional
     * @param includeImported      Whether a transitive closure of vocabulary imports should be used when getting the
     *                             root terms. Optional, defaults to {@code false}
     * @return List of root terms of the specific vocabulary
     */
    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/roots",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAllRoots(@PathVariable String vocabularyIdFragment,
                                     @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
                                     @RequestParam(name = QueryParams.PAGE_SIZE, required = false) Integer pageSize,
                                     @RequestParam(name = QueryParams.PAGE, required = false) Integer pageNo,
                                     @RequestParam(name = "includeImported", required = false) boolean includeImported,
                                     @RequestParam(name = "includeTerms", required = false,
                                                   defaultValue = "") List<URI> includeTerms) {
        final Vocabulary vocabulary = getVocabulary(getVocabularyUri(namespace, vocabularyIdFragment));
        return includeImported ?
                termService
                        .findAllRootsIncludingImported(vocabulary, createPageRequest(pageSize, pageNo), includeTerms) :
                termService.findAllRoots(vocabulary, createPageRequest(pageSize, pageNo), includeTerms);
    }

    /**
     * Creates a new root term in the specified vocabulary.
     *
     * @param vocabularyIdFragment Vocabulary name
     * @param namespace            Vocabulary namespace. Optional
     * @param term                 Vocabulary term that will be created
     * @return Response with {@code Location} header.
     * @see #createSubTerm(String, String, String, Term)
     */
    @PostMapping(value = "/vocabularies/{vocabularyIdFragment}/terms",
                 consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createRootTerm(@PathVariable String vocabularyIdFragment,
                                               @RequestParam(name = QueryParams.NAMESPACE, required = false)
                                                       Optional<String> namespace,
                                               @RequestBody Term term) {
        final URI vocabularyUri = getVocabularyUri(namespace, vocabularyIdFragment);
        termService.persistRoot(term, getVocabulary(vocabularyUri));

        LOG.debug("Root term {} created in vocabulary {}.", term, vocabularyUri);
        return ResponseEntity.created(generateLocation(term.getUri(), config.getNamespace().getVocabulary())).build();
    }

    /**
     * Gets term by its identifier fragment and vocabulary in which it is.
     *
     * @param vocabularyIdFragment Vocabulary identifier fragment
     * @param termIdFragment       Term identifier fragment
     * @param namespace            Vocabulary identifier namespace. Optional
     * @return Matching term
     * @throws NotFoundException If term does not exist
     */
    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Term getById(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                        @PathVariable("termIdFragment") String termIdFragment,
                        @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(vocabularyIdFragment, termIdFragment, namespace);
        return termService.findRequired(termUri);
    }

    /**
     * Gets term by its identifier.
     * <p>
     * This is a convenience method for accessing a Term without using its Vocabulary.
     *
     * @see #getById(String, String, Optional<String>)
     */
    @GetMapping(value = "/terms/{termIdFragment}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Term getById(@PathVariable("termIdFragment") String termIdFragment,
                        @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, termIdFragment);
        return termService.findRequired(termUri);
    }

    private URI getTermUri(String vocabIdFragment, String termIdFragment, Optional<String> namespace) {
        return idResolver.resolveIdentifier(idResolver
                .buildNamespace(getVocabularyUri(namespace, vocabIdFragment).toString(),
                        config.getNamespace().getTerm().getSeparator()), termIdFragment);
    }

    /**
     * Updates the specified term.
     *
     * @param vocabularyIdFragment Vocabulary identifier fragment
     * @param termIdFragment       Term identifier fragment
     * @param namespace            Vocabulary identifier namespace. Optional
     * @param term                 The updated term
     * @throws NotFoundException If term does not exist
     */
    @PutMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void update(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                       @PathVariable("termIdFragment") String termIdFragment,
                       @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
                       @RequestBody Term term) {
        final URI termUri = getTermUri(vocabularyIdFragment, termIdFragment, namespace);
        verifyRequestAndEntityIdentifier(term, termUri);
        termService.update(term);
        LOG.debug("Term {} updated.", term);
    }

    /**
     * Updates the specified term.
     * <p>
     * This is a convenience method for accessing a Term without using its Vocabulary.
     *
     * @see #update(String, String, Optional<String>, Term)
     */
    @PutMapping(value = "/terms/{termIdFragment}", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void update(@PathVariable("termIdFragment") String termIdFragment,
                       @RequestParam(name = QueryParams.NAMESPACE) String namespace,
                       @RequestBody Term term) {
        final URI termUri = idResolver.resolveIdentifier(namespace, termIdFragment);
        verifyRequestAndEntityIdentifier(term, termUri);
        termService.update(term);
        LOG.debug("Term {} updated.", term);
    }

    /**
     * Removes a term.
     *
     * @param vocabularyIdFragment vocabulary name
     * @param termIdFragment       term id fragment
     * @param namespace            (optional) vocabulary nanespace
     * @see TermService#remove(Term)  for details.
     */
    @DeleteMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void removeTerm(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                           @PathVariable("termIdFragment") String termIdFragment,
                           @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(vocabularyIdFragment, termIdFragment, namespace);
        termService.remove(termService.findRequired(termUri));
        LOG.debug("Term {} removed.", termUri);
    }

    /**
     * Returns terms not used in annotations/occurences of a resource for a given vocabulary
     */
    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/unused-terms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<URI> getUnusedTermsInVocabulary(
            @PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace
    ) {
        return termService.getUnusedTermsInVocabulary(getVocabulary(getVocabularyUri(namespace, vocabularyIdFragment)));
    }

    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}/subterms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Term> getSubTerms(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                                  @PathVariable("termIdFragment") String termIdFragment,
                                  @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final Term parent = getById(vocabularyIdFragment, termIdFragment, namespace);
        return termService.findSubTerms(parent);
    }

    /**
     * A convenience endpoint for getting subterms of a Term without using its Vocabulary.
     */
    @GetMapping(value = "/terms/{termIdFragment}/subterms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Term> getSubTerms(@PathVariable("termIdFragment") String termIdFragment,
                                  @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final Term parent = getById(termIdFragment, namespace);
        return termService.findSubTerms(parent);
    }

    /**
     * Creates a new term under the specified parent Term in the specified vocabulary.
     *
     * @param vocabularyIdFragment Vocabulary name
     * @param parentIdFragment     Parent term identifier fragment
     * @param namespace            Vocabulary namespace. Optional
     * @param newTerm              Vocabulary term that will be created
     * @return Response with {@code Location} header.
     * @see #createRootTerm(String, Optional<String>, Term)
     */
    @PostMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}/subterms",
                 produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createSubTerm(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                                              @PathVariable("termIdFragment") String parentIdFragment,
                                              @RequestParam(name = QueryParams.NAMESPACE,
                                                            required = false) Optional<String> namespace,
                                              @RequestBody Term newTerm) {
        final Term parent = getById(vocabularyIdFragment, parentIdFragment, namespace);
        termService.persistChild(newTerm, parent);
        LOG.debug("Child term {} of parent {} created.", newTerm, parent);
        return ResponseEntity.created(createSubTermLocation(newTerm.getUri(), parentIdFragment)).build();
    }

    private URI createSubTermLocation(URI childUri, String parentIdFragment) {
        final String u = generateLocation(childUri, config.getNamespace().getVocabulary()).toString();
        return URI.create(u.replace("/" + parentIdFragment + "/subterms", ""));
    }

    /**
     * Creates a new term under the specified parent Term.
     *
     * @see #createSubTerm(String, String, Optional<String>, Term)
     */
    @PostMapping(value = "/terms/{termIdFragment}/subterms",
                 produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createSubTerm(@PathVariable("termIdFragment") String parentIdFragment,
                                              @RequestParam(name = QueryParams.NAMESPACE,
                                                            required = false) String namespace,
                                              @RequestBody Term newTerm) {
        final Term parent = getById(parentIdFragment, namespace);
        termService.persistChild(newTerm, parent);
        LOG.debug("Child term {} of parent {} created.", newTerm, parent);
        return ResponseEntity.created(createSubTermLocation(newTerm.getUri(), parentIdFragment)).build();
    }

    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}/assignments",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermAssignments> getAssignmentInfo(@PathVariable String vocabularyIdFragment,
                                                   @PathVariable String termIdFragment,
                                                   @RequestParam(name = QueryParams.NAMESPACE, required = false)
                                                           Optional<String> namespace) {
        final URI termUri = getTermUri(vocabularyIdFragment, termIdFragment, namespace);
        return termService.getAssignmentInfo(termService.getRequiredReference(termUri));
    }

    /**
     * Gets assignment info for the specified Term.
     * <p>
     * This is a convenience method to allow access without using the Term's parent Vocabulary.
     *
     * @see #getAssignmentInfo(String, String, Optional<String>)
     */
    @GetMapping(value = "/terms/{termIdFragment}/assignments", produces = {MediaType.APPLICATION_JSON_VALUE,
                                                                           JsonLd.MEDIA_TYPE})
    public List<TermAssignments> getAssignmentInfo(@PathVariable("termIdFragment") String termIdFragment,
                                                   @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, termIdFragment);
        return termService.getAssignmentInfo(termService.getRequiredReference(termUri));
    }

    @PutMapping(value = "/terms/{termIdFragment}/definition-source",
                consumes = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void setTermDefinitionSource(@PathVariable String termIdFragment,
                                        @RequestParam(name = QueryParams.NAMESPACE) String namespace,
                                        @RequestBody TermDefinitionSource definitionSource) {
        final URI termUri = idResolver.resolveIdentifier(namespace, termIdFragment);
        termService.setTermDefinitionSource(termService.getRequiredReference(termUri), definitionSource);
        LOG.debug("Definition source of term {} set to {}.", termUri, definitionSource);
    }

    /**
     * Removes occurrence of a term in another term definition.
     */
    @DeleteMapping(value = "occurrence/{normalizedName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void removeOccurrence(@PathVariable String normalizedName,
                                 @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI identifier = idResolver.resolveIdentifier(namespace, normalizedName);
        termService.removeOccurrence(termService.getRequiredOccurrenceReference(identifier));
        LOG.debug("Occurrence with identifier {} removed.", identifier);
    }

    /**
     * Approves occurrence of a term in another term definition.
     */
    @PutMapping(value = "occurrence/{normalizedName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void approveOccurrence(@PathVariable String normalizedName,
                                  @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI identifier = idResolver.resolveIdentifier(namespace, normalizedName);

        termService.approveOccurrence(identifier);
        LOG.debug("Occurrence with identifier {} approved.", identifier);
    }

    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}/history",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                                                 @PathVariable("termIdFragment") String termIdFragment,
                                                 @RequestParam(name = QueryParams.NAMESPACE,
                                                               required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(vocabularyIdFragment, termIdFragment, namespace);
        return termService.getChanges(termService.getRequiredReference(termUri));
    }

    /**
     * Gets history of changes of the specified Term.
     * <p>
     * This is a convenience method to allow access without using the Term's parent Vocabulary.
     *
     * @see #getHistory(String, String, Optional<String>)
     */
    @GetMapping(value = "/terms/{termIdFragment}/history",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(@PathVariable("termIdFragment") String termIdFragment,
                                                 @RequestParam(name = QueryParams.NAMESPACE,
                                                               required = false) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, termIdFragment);
        return termService.getChanges(termService.getRequiredReference(termUri));
    }

    /**
     * Gets comments for the specified term.
     *
     * @return List of comments
     */
    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}/comments",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getComments(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                                     @PathVariable("termIdFragment") String termIdFragment,
                                     @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(vocabularyIdFragment, termIdFragment, namespace);
        return termService.getComments(termService.getRequiredReference(termUri));
    }

    /**
     * Gets comments for the specified term.
     * <p>
     * This is a convenience method to allow access without using the Term's parent Vocabulary.
     *
     * @see #getComments(String, String, Optional<String>)
     */
    @GetMapping(value = "/terms/{termIdFragment}/comments",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getComments(@PathVariable("termIdFragment") String termIdFragment,
                                     @RequestParam(name = QueryParams.NAMESPACE, required = false) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, termIdFragment);
        return termService.getComments(termService.getRequiredReference(termUri));
    }

    /**
     * Adds the specified comment to the specified term.
     */
    @PostMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}/comments",
                 consumes = {MediaType.APPLICATION_JSON_VALUE,
                             JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> addComment(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                                           @PathVariable("termIdFragment") String termIdFragment,
                                           @RequestParam(name = QueryParams.NAMESPACE,
                                                         required = false) Optional<String> namespace,
                                           @RequestBody Comment comment) {
        final Term term = termService.getRequiredReference(getTermUri(vocabularyIdFragment, termIdFragment, namespace));
        termService.addComment(comment, term);
        LOG.debug("Comment added to term {}.", term);
        return ResponseEntity.created(RestUtils
                .createLocationFromCurrentContextWithPathAndQuery("/comments/{name}", QueryParams.NAMESPACE,
                        IdentifierResolver.extractIdentifierNamespace(comment.getUri()),
                        IdentifierResolver.extractIdentifierFragment(comment.getUri()))).build();
    }

    /**
     * Adds the specified comment to the specified term.
     * <p>
     * This is a convenience method to allow access without using the Term's parent Vocabulary.
     *
     * @see #addComment(String, String, Optional<String>, Comment)
     */
    @PostMapping(value = "/terms/{termIdFragment}/comments", consumes = {MediaType.APPLICATION_JSON_VALUE,
                                                                         JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> addComment(@PathVariable("termIdFragment") String termIdFragment,
                                           @RequestParam(name = QueryParams.NAMESPACE,
                                                         required = false) String namespace,
                                           @RequestBody Comment comment) {
        final Term term = termService.getRequiredReference(idResolver.resolveIdentifier(namespace, termIdFragment));
        termService.addComment(comment, term);
        LOG.debug("Comment added to term {}.", term);
        return ResponseEntity.created(RestUtils
                .createLocationFromCurrentContextWithPathAndQuery("/comments/{name}", QueryParams.NAMESPACE,
                        IdentifierResolver.extractIdentifierNamespace(comment.getUri()),
                        IdentifierResolver.extractIdentifierFragment(comment.getUri()))).build();
    }

    /**
     * Get all root terms from all vocabularies
     * <p>
     * Optionally, the terms can be filtered by the specified search string, so that only roots with descendants with
     * label matching the specified string are returned.
     *
     * @param pageSize     Limit the number of elements in the returned page. Optional
     * @param pageNo       Number of the page to return. Optional
     * @param includeTerms List of terms to include in the results. Optional
     * @return List of root terms across all vocabularies
     */
    @GetMapping(value = "/terms/roots",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAllRoots(
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = Constants.QueryParams.PAGE, required = false) Integer pageNo,
            @RequestParam(name = "includeTerms", required = false, defaultValue = "") List<URI> includeTerms) {
        return termService.findAllRoots(createPageRequest(pageSize, pageNo), includeTerms);
    }

    /**
     * Get all terms preferred labels of which match the given searchString.
     *
     * @param searchString String to filter term labels by.
     * @return List of terms of the specific vocabulary.
     */
    @GetMapping(value = "/terms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAll(@RequestParam String searchString) {
        return termService.findAll(searchString);
    }
}
