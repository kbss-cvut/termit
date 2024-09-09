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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.rest.doc.ApiDocConstants;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.export.ExportConfig;
import cz.cvut.kbss.termit.service.export.ExportType;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.NotAcceptableStatusException;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cz.cvut.kbss.termit.rest.util.RestUtils.createPageRequest;

@Tag(name = "Terms", description = "Term management API")
@RestController
@RequestMapping("/")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
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
     * This method also allows exporting the terms into additional data formats (e.g., Excel, Turtle, RDF/XML) by using
     * HTTP content type negotiation or filter terms by a search string.
     *
     * @param localName       Vocabulary name
     * @param namespace       Vocabulary namespace. Optional
     * @param searchString    String to filter term labels by. Optional
     * @param includeImported Whether to include imported vocabularies when searching for terms. Does not apply to term
     *                        export. Optional, defaults to false
     * @param exportType      Type of the export. Optional
     * @param properties      A set of properties representing references to terms from other vocabularies to take into
     *                        account in export. Relevant only for term export. Optional
     * @param acceptType      MIME type accepted by the client, relevant only for term export
     * @return List of terms of the specific vocabulary
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets terms from the vocabulary with the specified identifier. " +
                       "HTTP content negotiation can be used to export the terms in supported formats (e.g., Turtle, RDF/XML, Excel).")
    @ApiResponse(responseCode = "200", description = "List of vocabulary terms.")
    @GetMapping(value = "/vocabularies/{localName}/terms",
                produces = {MediaType.APPLICATION_JSON_VALUE,
                            JsonLd.MEDIA_TYPE,
                            Constants.MediaType.EXCEL,
                            Constants.MediaType.TURTLE,
                            Constants.MediaType.RDF_XML})
    public ResponseEntity<?> getAll(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "String by which filter the terms (label).")
            @RequestParam(name = "searchString", required = false) String searchString,
            @Parameter(description = "Whether to include terms from imported vocabularies.")
            @RequestParam(name = "includeImported", required = false) boolean includeImported,
            @Parameter(description = "Type of export (applicable if HTTP accept type is neither JSON nor JSON-LD).")
            @RequestParam(name = "exportType", required = false) ExportType exportType,
            @Parameter(description = "Identifiers of properties to include in the export.")
            @RequestParam(name = "property", required = false,
                          defaultValue = "") Set<String> properties,
            @Parameter(
                    description = "HTTP Accept header. If its value is not JSON or JSON-LD, the request is interpreted as data export.")
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false,
                           defaultValue = MediaType.ALL_VALUE) String acceptType) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        final Vocabulary vocabulary = getVocabulary(vocabularyUri);
        if (searchString != null) {
            return ResponseEntity.ok(includeImported ?
                                     termService.findAllIncludingImported(searchString, vocabulary) :
                                     termService.findAll(searchString, vocabulary));
        }
        final Optional<ResponseEntity<?>> export = exportTerms(vocabulary, exportType, properties, acceptType);
        return export.orElseGet(() -> {
            verifyAcceptType(acceptType);
            return ResponseEntity
                    .ok(includeImported ? termService.findAllIncludingImported(vocabulary) :
                        termService.findAll(vocabulary));
        });
    }

    private Optional<ResponseEntity<?>> exportTerms(Vocabulary vocabulary, ExportType exportType,
                                                    Set<String> properties, String mediaType) {
        if (exportType == null) {
            return Optional.empty();
        }
        final ExportConfig config = new ExportConfig(exportType, mediaType, properties);
        final Optional<TypeAwareResource> content = termService.exportGlossary(vocabulary, config);
        return content.map(r -> {
            try {
                return ResponseEntity.ok()
                                     .contentLength(r.contentLength())
                                     .contentType(MediaType.parseMediaType(mediaType))
                                     .header(HttpHeaders.CONTENT_DISPOSITION,
                                             "attachment; filename=\"" + IdentifierResolver.normalizeToAscii(
                                                     IdentifierResolver.extractIdentifierFragment(
                                                             vocabulary.getUri())) +
                                                     r.getFileExtension().orElse("") + "\"")
                                     .body(r);
            } catch (IOException e) {
                throw new TermItException("Unable to export terms.", e);
            }
        });
    }

    private void verifyAcceptType(String acceptType) {
        if (!JsonLd.MEDIA_TYPE.equals(acceptType) && !MediaType.APPLICATION_JSON_VALUE.equals(
                acceptType) && !MediaType.ALL_VALUE.equals(acceptType)) {
            throw new NotAcceptableStatusException(
                    "Media type " + acceptType + " not supported for term retrieval. If you are attempting to export terms, do not forget to add exportType parameter.");
        }
    }

    /**
     * Performs simple vocabulary-based checks. If {@code prefLabel} is passed in, it checks whether a term with the
     * given pref label exists in the given vocabulary for the given language. If not, this method returns the number of
     * terms in the specified vocabulary in a special response header.
     *
     * @param localName vocabulary id fragment
     * @param namespace vocabulary namespace
     * @param prefLabel the label to check, optional
     * @param language  language to check existence in, optional
     * @return OK response when term exists and label check was performed, Not Found otherwise. If no label to check was
     * passed, an empty OK response with header containing total number of terms is returned
     */
    @Operation(
            description = "Checks if a term with the specified label (in the specified language) exists in the vocabulary with the specified identifier. If no label is provided, this method checks for the number of terms in the target vocabulary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "Term with matching label already exists. If no label was specified, a header contains the total number of terms in the vocabulary."),
            @ApiResponse(responseCode = "404",
                         description = "Vocabulary with specified identifier not found. Or no term with matching label exists.")
    })
    @PreAuthorize("permitAll()")
    @RequestMapping(method = RequestMethod.HEAD, value = "/vocabularies/{localName}/terms")
    public ResponseEntity<Void> checkTerms(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "Label for whose existence to check.")
            @RequestParam(name = "prefLabel", required = false) String prefLabel,
            @Parameter(description = "Language of the label.")
            @RequestParam(name = "language", required = false) String language) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);

            final Vocabulary vocabulary = termService.getVocabularyReference(vocabularyUri);
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
     * @param localName       Vocabulary name
     * @param namespace       Vocabulary namespace. Optional
     * @param pageSize        Limit the number of elements in the returned page. Optional
     * @param pageNo          Number of the page to return. Optional
     * @param includeImported Whether a transitive closure of vocabulary imports should be used when getting the root
     *                        terms. Optional, defaults to {@code false}
     * @return List of root terms of the specific vocabulary
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets root terms (terms without parent) from the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of root vocabulary terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/roots",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAllRoots(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
            @RequestParam(name = QueryParams.PAGE_SIZE, required = false) Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
            @RequestParam(name = QueryParams.PAGE, required = false) Integer pageNo,
            @Parameter(description = "Whether to include terms from imported vocabularies.")
            @RequestParam(name = "includeImported", required = false) boolean includeImported,
            @Parameter(
                    description = "Identifiers of terms that should be included in the response (regardless of whether they are root terms or not).")
            @RequestParam(name = "includeTerms", required = false, defaultValue = "") List<URI> includeTerms) {

        final Vocabulary vocabulary = getVocabulary(getVocabularyUri(namespace, localName));
        return includeImported ?
                termService
                        .findAllRootsIncludingImported(vocabulary, createPageRequest(pageSize, pageNo), includeTerms) :
                termService.findAllRoots(vocabulary, createPageRequest(pageSize, pageNo), includeTerms);

    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new root terms in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Term created."),
            @ApiResponse(responseCode = "404", description = "Vocabulary not found."),
            @ApiResponse(responseCode = "409", description = "Term data invalid.")
    })
    @PostMapping(value = "/vocabularies/{localName}/terms",
                 consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createRootTerm(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "The new term.")
            @RequestBody Term term) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        termService.persistRoot(term, getVocabulary(vocabularyUri));

        LOG.debug("Root term {} created in vocabulary {}.", term, vocabularyUri);
        return ResponseEntity.created(generateLocation(term.getUri(), config.getNamespace().getVocabulary())).build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the term with the specified local name from the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Term detail."),
            @ApiResponse(responseCode = "404", description = "Vocabulary not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Term getById(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.findRequired(termUri);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Term detail."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Term getById(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.findRequired(termUri);
    }

    private URI getTermUri(String vocabIdFragment, String termIdFragment, Optional<String> namespace) {
        return idResolver.resolveIdentifier(idResolver.buildNamespace(
                                                    getVocabularyUri(namespace, vocabIdFragment).toString(),
                                                    config.getNamespace().getTerm().getSeparator()),
                                            termIdFragment);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates the term with the specified local name from the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term successfully updated."),
            @ApiResponse(responseCode = "404", description = "Vocabulary not found."),
            @ApiResponse(responseCode = "409", description = "Term data invalid.")
    })
    @PutMapping(value = "/vocabularies/{localName}/terms/{termLocalName}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "Updated term data.")
            @RequestBody Term term) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        verifyRequestAndEntityIdentifier(term, termUri);
        termService.update(term);
        LOG.debug("Term {} updated.", term);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term successfully updated."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Term data invalid.")
    })
    @PutMapping(value = "/terms/{localName}", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "Updated term data.")
            @RequestBody Term term) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        verifyRequestAndEntityIdentifier(term, termUri);
        termService.update(term);
        LOG.debug("Term {} updated.", term);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes the term with the specified local name from the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term successfully removed."),
            @ApiResponse(responseCode = "404", description = "Vocabulary not found."),
            @ApiResponse(responseCode = "409", description = "Unable to remove term.")
    })
    @DeleteMapping(value = "/vocabularies/{localName}/terms/{termLocalName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTerm(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        termService.remove(termService.findRequired(termUri));
        LOG.debug("Term {} removed.", termUri);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets sub-terms of the term with the specified local name in the vocabulary with the specified identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sub-terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/subterms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Term> getSubTerms(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final Term parent = getById(localName, termLocalName, namespace);
        return termService.findSubTerms(parent);
    }

    /**
     * A convenience endpoint for getting subterms of a Term without using its Vocabulary.
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets subterms of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term successfully updated."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/subterms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Term> getSubTerms(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final Term parent = getById(localName, namespace);
        return termService.findSubTerms(parent);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new sub-term of the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sub-term created."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or parent term not found."),
            @ApiResponse(responseCode = "409", description = "Term metadata invalid.")
    })
    @PostMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/subterms",
                 produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createSubTerm(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "The new term.")
            @RequestBody Term newTerm) {
        final Term parent = getById(localName, termLocalName, namespace);
        termService.persistChild(newTerm, parent);
        LOG.debug("Child term {} of parent {} created.", newTerm, parent);
        return ResponseEntity.created(createSubTermLocation(newTerm.getUri(), termLocalName)).build();
    }

    private URI createSubTermLocation(URI childUri, String parentIdFragment) {
        final String u = generateLocation(childUri, config.getNamespace().getVocabulary()).toString();
        return URI.create(u.replace("/" + parentIdFragment + "/subterms", ""));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new sub-term of the term with the specified local identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sub-term created."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Term metadata invalid.")
    })
    @PostMapping(value = "/terms/{localName}/subterms",
                 produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createSubTerm(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace,
            @RequestBody Term newTerm) {
        final Term parent = getById(localName, namespace);
        termService.persistChild(newTerm, parent);
        LOG.debug("Child term {} of parent {} created.", newTerm, parent);
        return ResponseEntity.created(createSubTermLocation(newTerm.getUri(), localName)).build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of all other terms that are related via definition to the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definition-related terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/def-related-target", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<TermOccurrence> getDefinitionallyRelatedTermsTargeting(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.getDefinitionallyRelatedTargeting(termService.findRequired(termUri));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of all other terms that appear in the definition of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definition-related terms."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/def-related-target", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<TermOccurrence> getDefinitionallyRelatedTermsTargeting(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.getDefinitionallyRelatedTargeting(termService.findRequired(termUri));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of all other terms whose definition contains the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definition-related terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/def-related-of", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<TermOccurrence> getDefinitionallyRelatedTermsOf(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.getDefinitionallyRelatedOf(termService.findRequired(termUri));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of all other terms whose definition contains the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definition-related terms."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/def-related-of", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<TermOccurrence> getDefinitionallyRelatedTermsOf(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.getDefinitionallyRelatedOf(termService.findRequired(termUri));
    }

    /**
     * Runs text analysis on the specified Term's definition.
     * <p>
     * This is a legacy endpoint intended mainly for internal use/testing, since the analysis is executed automatically
     * when specific conditions are fulfilled.
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Runs text analysis on the definition of  term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definition-related terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @PutMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/text-analysis")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void runTextAnalysisOnTerm(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        termService.analyzeTermDefinition(getById(localName, termLocalName, namespace), getVocabularyUri(namespace, localName));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Sets the source of definition of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Definition source set."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @PutMapping(value = "/terms/{localName}/definition-source",
                consumes = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setTermDefinitionSource(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "Definition source to set.")
            @RequestBody TermDefinitionSource definitionSource) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        termService.setTermDefinitionSource(termService.findRequired(termUri), definitionSource);
        LOG.debug("Definition source of term {} set to {}.", termUri, definitionSource);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes source of definition of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Definition source removed."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @DeleteMapping(value = "/terms/{localName}/definition-source")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTermDefinitionSource(@Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                                                      example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
                                           @PathVariable String localName,
                                           @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                                                      example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
                                           @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        final Term term = termService.findRequired(termUri);
        termService.removeTermDefinitionSource(term);
        LOG.debug("Definition source of term {} removed.", term);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Sets state of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term state successfully set."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @PutMapping(value = "terms/{localName}/state", consumes = MediaType.ALL_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "The state to set.")
            @RequestBody String state) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        final URI stateUri = URI.create(state);
        final Term t = termService.findRequired(termUri);
        termService.setState(t, stateUri);
        LOG.debug("State of term {} set to {}.", t, Utils.uriToString(stateUri));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of changes made to metadata of the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of change records."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/history",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.getChanges(termService.findRequired(termUri));
    }

    /**
     * Gets history of changes of the specified Term.
     * <p>
     * This is a convenience method to allow access without using the Term's parent Vocabulary.
     *
     * @see #getHistory(String, String, Optional)
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of changes made to metadata of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of change records."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/history",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(@Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                                                            example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
                                                 @PathVariable String localName,
                                                 @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                                                            example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
                                                 @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.getChanges(termService.findRequired(termUri));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of comments on the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of comments."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/comments",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getComments(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "Datetime (ISO-formatted) of the oldest comment to retrieve.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "from", required = false) Optional<String> from,
            @Parameter(description = "Datetime (ISO-formatted) of the latest comment to retrieve. Defaults to now.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "to", required = false) Optional<String> to) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.getComments(termService.findRequired(termUri),
                                       from.map(RestUtils::parseTimestamp).orElse(Constants.EPOCH_TIMESTAMP),
                                       to.map(RestUtils::parseTimestamp).orElse(Utils.timestamp()));
    }

    /**
     * Gets comments for the specified term.
     * <p>
     * This is method allows access without using the Term's Vocabulary.
     *
     * @see #getComments(String, String, Optional, Optional, Optional)
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of comments on the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of comments."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/comments",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getComments(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "Datetime (ISO-formatted) of the oldest comment to retrieve.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "from", required = false) Optional<String> from,
            @Parameter(description = "Datetime (ISO-formatted) of the latest comment to retrieve. Defaults to now.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "to", required = false) Optional<String> to) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.getComments(termService.findRequired(termUri),
                                       from.map(RestUtils::parseTimestamp).orElse(Constants.EPOCH_TIMESTAMP),
                                       to.map(RestUtils::parseTimestamp).orElse(Utils.timestamp()));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Adds a comment to the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment successfully added."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")

    })
    @PostMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/comments",
                 consumes = {MediaType.APPLICATION_JSON_VALUE,
                             JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> addComment(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "Comment to add.")
            @RequestBody Comment comment) {
        final Term term = termService.findRequired(getTermUri(localName, termLocalName, namespace));
        termService.addComment(comment, term);
        LOG.debug("Comment added to term {}.", term);
        return ResponseEntity.created(RestUtils
                                              .createLocationFromCurrentContextWithPathAndQuery("/comments/{name}",
                                                                                                QueryParams.NAMESPACE,
                                                                                                IdentifierResolver.extractIdentifierNamespace(
                                                                                                        comment.getUri()),
                                                                                                IdentifierResolver.extractIdentifierFragment(
                                                                                                        comment.getUri())))
                             .build();
    }

    /**
     * Adds the specified comment to the specified term.
     * <p>
     * This is a convenience method to allow access without using the Term's parent Vocabulary.
     *
     * @see #addComment(String, String, Optional, Comment)
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Adds a comment to the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment successfully added."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)

    })
    @PostMapping(value = "/terms/{localName}/comments", consumes = {MediaType.APPLICATION_JSON_VALUE,
                                                                    JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> addComment(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "Comment to add.")
            @RequestBody Comment comment) {
        final Term term = termService.findRequired(idResolver.resolveIdentifier(namespace, localName));
        termService.addComment(comment, term);
        LOG.debug("Comment added to term {}.", term);
        return ResponseEntity.created(RestUtils
                                              .createLocationFromCurrentContextWithPathAndQuery("/comments/{name}",
                                                                                                QueryParams.NAMESPACE,
                                                                                                IdentifierResolver.extractIdentifierNamespace(
                                                                                                        comment.getUri()),
                                                                                                IdentifierResolver.extractIdentifierFragment(
                                                                                                        comment.getUri())))
                             .build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of snapshots of the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "A list of snapshots or a snapshot valid at the requested datetime."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")

    })
    @GetMapping(value = "vocabularies/{localName}/terms/{termLocalName}/versions",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION, example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION, example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "Timestamp (ISO formatted) at which the returned version was valid.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "at", required = false) Optional<String> at) {
        final Term term = termService.findRequired(getTermUri(localName, termLocalName, namespace));
        return getTermSnapshots(at, term);
    }

    private ResponseEntity<?> getTermSnapshots(Optional<String> at, Term term) {
        if (at.isPresent()) {
            final Instant instant = RestUtils.parseTimestamp(at.get());
            return ResponseEntity.ok(termService.findVersionValidAt(term, instant));
        }
        return ResponseEntity.ok(termService.findSnapshots(term));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of snapshots of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "A list of snapshots or a snapshot valid at the requested datetime."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)

    })
    @GetMapping(value = "/terms/{localName}/versions",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(
            @Parameter(description = ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "Timestamp (ISO-formatted) at which the returned version was valid.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "at", required = false) Optional<String> at) {
        final Term term = termService.findRequired(idResolver.resolveIdentifier(namespace, localName));
        return getTermSnapshots(at, term);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of root terms from all vocabularies.")
    @ApiResponse(responseCode = "200", description = "A list of terms.")
    @GetMapping(value = "/terms/roots", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAllRoots(
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false) Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE, required = false) Integer pageNo,
            @Parameter(description = "List of identifiers of terms that should be included in the result.")
            @RequestParam(name = "includeTerms", required = false, defaultValue = "") List<URI> includeTerms) {
        return termService.findAllRoots(createPageRequest(pageSize, pageNo), includeTerms);
    }

    /**
     * Get all terms preferred labels of which match the given searchString.
     *
     * @param searchString String to filter term labels by.
     * @return List of terms of the specific vocabulary.
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets terms whose label matches the specified search string, regardless of their vocabulary.")
    @ApiResponse(responseCode = "200", description = "A list of matching terms.")
    @GetMapping(value = "/terms", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAll(@Parameter(description = "String by which the terms should be filtered.")
                                @RequestParam String searchString) {
        return termService.findAll(searchString);
    }

    /**
     * A couple of constants for the {@link TermController} API documentation.
     */
    public static final class ApiDoc {
        public static final String ID_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace/default vocabulary namespace) unique part of the vocabulary identifier.";
        public static final String ID_LOCAL_NAME_EXAMPLE = "datovy-mpp-3.4";
        public static final String ID_NAMESPACE_DESCRIPTION = "Identifier namespace. Allows to override the default vocabulary identifier namespace.";
        public static final String ID_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/slovnik/";
        public static final String ID_TERM_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the vocabulary identifier) unique part of the term identifier.";
        public static final String ID_TERM_LOCAL_NAME_EXAMPLE = "struktura";
        public static final String ID_STANDALONE_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace) unique part of the term identifier.";
        public static final String ID_STANDALONE_NAMESPACE_DESCRIPTION = "Term identifier namespace";
        public static final String ID_STANDALONE_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/slovnik/datovy-mpp-3.4/pojem/";
        public static final String ID_STANDALONE_NOT_FOUND_DESCRIPTION = "Term with the specified identifier not found.";

        private ApiDoc() {
            throw new AssertionError();
        }
    }
}
