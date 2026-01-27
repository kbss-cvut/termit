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
package cz.cvut.kbss.termit.rest.readonly;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.rest.BaseController;
import cz.cvut.kbss.termit.rest.TermController;
import cz.cvut.kbss.termit.rest.doc.ApiDocConstants;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.readonly.ReadOnlyTermService;
import cz.cvut.kbss.termit.service.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.rest.util.RestUtils.createPageRequest;
import static cz.cvut.kbss.termit.security.SecurityConstants.PUBLIC_API_PATH;

@Profile("!no-public-api")
@Tag(name = "Terms - public", description = "No-authorization term API")
@RestController
@PreAuthorize("permitAll()")
@RequestMapping(PUBLIC_API_PATH)
public class ReadOnlyTermController extends BaseController {

    private final ReadOnlyTermService termService;

    private final VocabularyNamespaceResolver namespaceResolver;

    public ReadOnlyTermController(IdentifierResolver idResolver, Configuration config,
                                  ReadOnlyTermService termService, VocabularyNamespaceResolver namespaceResolver) {
        super(idResolver, config);
        this.termService = termService;
        this.namespaceResolver = namespaceResolver;
    }

    @Operation(description = "Gets terms from the vocabulary with the specified identifier.")
    @ApiResponse(responseCode = "200", description = "List of vocabulary terms.")
    @GetMapping(value = "/vocabularies/{localName}/terms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<?> getTerms(@Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                       example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                            @PathVariable String localName,
                            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                       example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                            @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                          required = false) Optional<String> namespace,
                            @Parameter(description = "String by which filter the terms (label).")
                            @RequestParam(name = "searchString", required = false) String searchString,
                            @Parameter(description = "Whether to include terms from imported vocabularies.")
                            @RequestParam(name = "includeImported", required = false) boolean includeImported,
                            @Parameter(description = "Boolean flag to determine whether the list should be flattened.")
                                @RequestParam(name = "flat", required = false, defaultValue = "false") boolean flat,
                            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
                            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false) Integer pageSize,
                            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
                            @RequestParam(name = Constants.QueryParams.PAGE, required = false) Integer pageNo) {
        final Vocabulary vocabulary = getVocabulary(localName, namespace);
        if (searchString != null) {
            if (flat) {
                return includeImported ? termService.findAllFlatIncludingImported(searchString, vocabulary,
                                                                                 createPageRequest(pageSize, pageNo)) :
                                   termService.findAllFlat(searchString, vocabulary,
                                                          createPageRequest(pageSize, pageNo));
            }
            return includeImported ? termService.findAllIncludingImported(searchString, vocabulary, createPageRequest(pageSize, pageNo)) :
                   termService.findAll(searchString, vocabulary, createPageRequest(pageSize, pageNo));
        }
        return flat ? termService.findAllFlat(vocabulary, createPageRequest(pageSize, pageNo)) :
                   termService.findAll(vocabulary, createPageRequest(pageSize, pageNo));
    }

    private Vocabulary getVocabulary(String fragment, Optional<String> namespace) {
        final URI uri = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        return termService.findVocabularyRequired(uri);
    }

    @Operation(
            description = "Gets root terms (terms without parent) from the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of root vocabulary terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/roots",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAllRoots(
            @Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false) Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE, required = false) Integer pageNo,
            @Parameter(description = "Whether to include terms from imported vocabularies.")
            @RequestParam(name = "includeImported", required = false) boolean includeImported) {
        final Vocabulary vocabulary = getVocabulary(localName, namespace);
        final Pageable pageSpec = RestUtils.createPageRequest(pageSize, pageNo);
        return includeImported ? termService.findAllRootsIncludingImported(vocabulary, pageSpec) :
               termService.findAllRoots(vocabulary, pageSpec);
    }

    @Operation(
            description = "Gets the term with the specified local name from the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Term detail."),
            @ApiResponse(responseCode = "404", description = "Vocabulary not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ReadOnlyTerm getById(
            @Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.findRequired(termUri);
    }

    private URI getTermUri(String vocabIdFragment, String termIdFragment, Optional<String> namespace) {
        final URI vocabularyUri = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), vocabIdFragment);
        return resolveIdentifier(namespaceResolver.resolveNamespace(vocabularyUri), termIdFragment);
    }

    @Operation(description = "Gets the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Term detail."),
            @ApiResponse(responseCode = "404", description = TermController.ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ReadOnlyTerm getById(
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace
    ) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.findRequired(termUri);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets basic information about a term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Basic term information."),
            @ApiResponse(responseCode = "404", description = TermController.ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/info", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public TermInfo getTermInfoById(@Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                                               example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
                                    @PathVariable String localName,
                                    @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                                               example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
                                    @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.findRequiredTermInfo(termUri);
    }

    @Operation(
            description = "Gets sub-terms of the term with the specified local name in the vocabulary with the specified identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sub-terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/subterms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getSubTerms(
            @Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final ReadOnlyTerm parent = getById(localName, termLocalName, namespace);
        return termService.findSubTerms(parent);
    }

    @Operation(description = "Gets subterms of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term successfully updated."),
            @ApiResponse(responseCode = "404", description = TermController.ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/subterms",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getSubTerms(
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final ReadOnlyTerm parent = getById(localName, namespace);
        return termService.findSubTerms(parent);
    }

    @Operation(
            description = "Gets a list of comments on the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of comments."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/comments",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getComments(
            @Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "Datetime (ISO-formatted) of the oldest comment to retrieve.")
            @RequestParam(name = "from", required = false) Optional<String> from,
            @Parameter(description = "Datetime (ISO-formatted) of the latest comment to retrieve. Defaults to now.")
            @RequestParam(name = "to", required = false) Optional<String> to) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.getComments(termService.getReference(termUri),
                                       from.map(RestUtils::parseTimestamp).orElse(Constants.EPOCH_TIMESTAMP),
                                       to.map(RestUtils::parseTimestamp).orElse(Utils.timestamp()));
    }

    @Operation(
            description = "Gets a list of all other terms whose definition contains the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definition-related terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/def-related-of", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<TermOccurrence> getDefinitionallyRelatedTermsOf(
            @Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.getDefinitionallyRelatedOf(termService.getReference(termUri));
    }

    @Operation(
            description = "Gets a list of all other terms that are related via definition to the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definition-related terms."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")
    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/def-related-target", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<TermOccurrence> getDefinitionallyRelatedTermsTargeting(
            @Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String termLocalName,
            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(localName, termLocalName, namespace);
        return termService.getDefinitionallyRelatedTargeting(termService.getReference(termUri));
    }

    @Operation(description = "Gets a list of comments on the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of comments."),
            @ApiResponse(responseCode = "404", description = TermController.ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/terms/{localName}/comments",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getComments(
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "Datetime (ISO-formatted) of the oldest comment to retrieve.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "from", required = false) Optional<String> from,
            @Parameter(description = "Datetime (ISO-formatted) of the latest comment to retrieve. Defaults to now.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "to", required = false) Optional<String> to) {
        final URI termUri = idResolver.resolveIdentifier(namespace, localName);
        return termService.getComments(termService.getReference(termUri),
                                       from.map(RestUtils::parseTimestamp).orElse(Constants.EPOCH_TIMESTAMP),
                                       to.map(RestUtils::parseTimestamp).orElse(Utils.timestamp()));
    }

    @Operation(
            description = "Gets a list of snapshots of the term with the specified local name in the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "A list of snapshots or a snapshot valid at the requested datetime."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or term term not found.")

    })
    @GetMapping(value = "/vocabularies/{localName}/terms/{termLocalName}/versions",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(@Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                     example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                          @PathVariable String localName,
                                          @Parameter(description = TermController.ApiDoc.ID_TERM_LOCAL_NAME_DESCRIPTION,
                                                     example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
                                          @PathVariable String termLocalName,
                                          @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                     example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                                          @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @Parameter(
                                                  description = "Timestamp (ISO-formatted) at which the returned version was valid.",
                                                  example = ApiDocConstants.DATETIME_EXAMPLE)
                                          @RequestParam(name = "at", required = false) Optional<String> at) {
        final ReadOnlyTerm term = getById(localName, termLocalName, namespace);
        return getTermSnapshots(at, term);
    }

    private ResponseEntity<?> getTermSnapshots(Optional<String> at, ReadOnlyTerm term) {
        if (at.isPresent()) {
            final Instant instant = RestUtils.parseTimestamp(at.get());
            return ResponseEntity.ok(termService.findVersionValidAt(term, instant));
        }
        return ResponseEntity.ok(termService.findSnapshots(term));
    }

    @Operation(description = "Gets a list of snapshots of the term with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "A list of snapshots or a snapshot valid at the requested datetime."),
            @ApiResponse(responseCode = "404", description = TermController.ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)

    })
    @GetMapping(value = "/terms/{localName}/versions",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
            @Parameter(description = "Timestamp (ISO-formatted) at which the returned version was valid.",
                       example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "at", required = false) Optional<String> at) {
        final ReadOnlyTerm term = getById(localName, namespace);
        return getTermSnapshots(at, term);
    }
}
