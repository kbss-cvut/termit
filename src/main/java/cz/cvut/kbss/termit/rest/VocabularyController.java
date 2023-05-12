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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Tag(name = "Vocabularies", description = "Vocabulary management API")
@RestController
@RequestMapping("/vocabularies")
public class VocabularyController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(VocabularyController.class);

    private final VocabularyService vocabularyService;

    @Autowired
    public VocabularyController(VocabularyService vocabularyService, IdentifierResolver idResolver,
                                Configuration config) {
        super(idResolver, config);
        this.vocabularyService = vocabularyService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets all vocabularies managed by the system.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of vocabularies ordered by label.")
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<List<VocabularyDto>> getAll(ServletWebRequest webRequest) {
        if (webRequest.checkNotModified(vocabularyService.getLastModified())) {
            return null;
        }
        return ResponseEntity.ok().lastModified(vocabularyService.getLastModified()).body(vocabularyService.findAll());
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")}, description = "Creates a new vocabulary.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vocabulary successfully created."),
            @ApiResponse(responseCode = "409",
                         description = "Vocabulary with the same identifier already exists or the vocabulary is not valid.")
    })
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(@Parameter(description = "Metadata of the vocabulary to create.")
                                                 @RequestBody Vocabulary vocabulary) {
        vocabularyService.persist(vocabulary);
        LOG.debug("Vocabulary {} created.", vocabulary);
        return ResponseEntity.created(generateLocation(vocabulary.getUri())).build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets detail of a vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching vocabulary metadata."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{fragment}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Vocabulary getById(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                         example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                              @PathVariable String fragment,
                              @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                         example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                              @RequestParam(name = QueryParams.NAMESPACE,
                                            required = false) Optional<String> namespace) {
        final URI id = resolveVocabularyUri(fragment, namespace);
        return vocabularyService.findRequired(id);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets identifiers of vocabularies imported (including transitive imports) by the vocabulary with the specified identification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection of vocabulary identifiers."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{fragment}/imports", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getImports(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                 example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                      @PathVariable String fragment,
                                      @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                 example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getTransitivelyImportedVocabularies(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets identifiers of vocabularies whose terms are in a SKOS relationship with terms from the specified vocabulary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection of vocabulary identifiers."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{fragment}/related", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getRelated(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                 example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                      @PathVariable String fragment,
                                      @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                 example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getRelatedVocabularies(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new vocabulary by importing the specified SKOS glossary.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vocabulary successfully created."),
            @ApiResponse(responseCode = "409",
                         description = "If a matching vocabulary already exists or the imported one contains invalid data.")
    })
    @PostMapping("/import")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(
            @Parameter(description = "File containing a SKOS glossary in RDF.")
            @RequestParam(name = "file") MultipartFile file,
            @Parameter(
                    description = "Whether identifiers should be modified to prevent clashes with existing data.")
            @RequestParam(name = "rename", defaultValue = "false", required = false) boolean rename) {
        final Vocabulary vocabulary = vocabularyService.importVocabulary(rename, file);
        LOG.debug("New vocabulary {} imported.", vocabulary);
        return ResponseEntity.created(locationWithout(generateLocation(vocabulary.getUri()), "/import")).build();
    }

    URI locationWithout(URI location, String toRemove) {
        return URI.create(location.toString().replace(toRemove, ""));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Imports a vocabulary from the specified SKOS glossary, possibly replacing existing vocabulary with matching identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vocabulary successfully created."),
            @ApiResponse(responseCode = "409", description = "If the imported file contains invalid data.")
    })
    @PostMapping("/{fragment}/import")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(
            @Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace,
            @Parameter(description = "File containing a SKOS glossary in RDF.")
            @RequestParam(name = "file") MultipartFile file) {
        final URI vocabularyIri = resolveVocabularyUri(fragment, namespace);
        final Vocabulary vocabulary = vocabularyService.importVocabulary(vocabularyIri, file);
        LOG.debug("Vocabulary {} re-imported.", vocabulary);
        return ResponseEntity.created(locationWithout(generateLocation(vocabulary.getUri()), "/import/" + fragment))
                             .build();
    }

    private URI resolveVocabularyUri(String fragment, Optional<String> namespace) {
        return resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of changes made to metadata of vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of change records."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)

    })
    @GetMapping(value = "/{fragment}/history", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(
            @Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getChanges(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets summary info about changes made to the content of the vocabulary (term creation, editing).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of aggregated change data."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{fragment}/history-of-content",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AggregatedChangeInfo> getHistoryOfContent(
            @Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getChangesOfContent(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates metadata of vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vocabulary successfully updated."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Provided metadata are invalid.")
    })
    @PutMapping(value = "/{fragment}", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void updateVocabulary(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                            example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                 @PathVariable String fragment,
                                 @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                            example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                 @RequestParam(name = QueryParams.NAMESPACE,
                                               required = false) Optional<String> namespace,
                                 @Parameter(description = "Updated vocabulary data.")
                                 @RequestBody Vocabulary update) {
        final URI vocabularyUri = resolveVocabularyUri(fragment, namespace);
        verifyRequestAndEntityIdentifier(update, vocabularyUri);
        vocabularyService.update(update);
        LOG.debug("Vocabulary {} updated.", update);
    }

    /**
     * Runs text analysis on definitions of all terms in vocabulary.
     * <p>
     * This is a legacy endpoint intended mainly for internal use/testing, since the analysis is executed automatically
     * when specific conditions are fulfilled.
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Runs text analysis on the definitions of all terms in the vocabulary with the specified identifier.")
    @PutMapping(value = "/{fragment}/terms/text-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void runTextAnalysisOnAllTerms(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                     example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                          @PathVariable String fragment,
                                          @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                     example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace) {
        vocabularyService.runTextAnalysisOnAllTerms(getById(fragment, namespace));
    }

    /**
     * Runs text analysis on definitions of all terms in all vocabularies.
     * <p>
     * This is a legacy endpoint intended mainly for internal use/testing, since the analysis is executed automatically
     * when specific conditions are fulfilled.
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Runs text analysis on definitions of all terms in all vocabularies.")
    @GetMapping(value = "/text-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    public void runTextAnalysisOnAllVocabularies() {
        vocabularyService.runTextAnalysisOnAllVocabularies();
    }

    /**
     * Removes a vocabulary.
     *
     * @param fragment  vocabulary name
     * @param namespace (optional) vocabulary namespace
     * @see VocabularyService#remove(Vocabulary)  for details.
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vocabulary successfully removed."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409",
                         description = "Unable to remove vocabulary. E.g., it contains terms, it is referenced by other vocabularies, etc.")
    })
    @DeleteMapping(value = "/{fragment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void removeVocabulary(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                            example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                 @PathVariable String fragment,
                                 @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                            example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                 @RequestParam(name = QueryParams.NAMESPACE,
                                               required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary toRemove = vocabularyService.getRequiredReference(identifier);
        vocabularyService.remove(toRemove);
        LOG.debug("Vocabulary {} removed.", toRemove);
    }

    @Operation(description = "Validates the terms in a vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A collection of validation results."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PreAuthorize("permitAll()")    // TODO Authorize?
    @GetMapping(value = "/{fragment}/validate",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<ValidationResult> validateVocabulary(
            @Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        return vocabularyService.validateContents(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a snapshot of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Snapshot successfully created."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PostMapping("/{fragment}/versions")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createSnapshot(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                          example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                               @PathVariable String fragment,
                                               @Parameter(
                                                       description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                       example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                               @RequestParam(name = QueryParams.NAMESPACE,
                                                             required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        final Snapshot snapshot = vocabularyService.createSnapshot(vocabulary);
        LOG.debug("Created snapshot of vocabulary {}.", vocabulary);
        return ResponseEntity.created(
                locationWithout(generateLocation(snapshot.getUri()), "/" + fragment + "/versions")).build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of snapshots of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "A list of snapshots or a snapshot valid at the requested datetime."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{fragment}/versions", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                     example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                          @PathVariable String fragment,
                                          @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                     example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @Parameter(
                                                  description = "Timestamp at which the returned valid was valid. ISO-formatted datetime.",
                                                  example = "2023-01-01T00:00:00")
                                          @RequestParam(name = "at", required = false) Optional<String> at) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        if (at.isPresent()) {
            final Instant instant = RestUtils.parseTimestamp(at.get());
            return ResponseEntity.ok(vocabularyService.findVersionValidAt(vocabulary, instant));
        }
        return ResponseEntity.ok(vocabularyService.findSnapshots(vocabulary));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the access control list of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access control list instance."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{fragment}/acl", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public AccessControlListDto getAccessControlList(
            @Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        return vocabularyService.getAccessControlList(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Adds the specified access control record to the access control list of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Record successfully added."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PostMapping(value = "/{fragment}/acl/records", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addAccessControlRecord(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                  example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                       @PathVariable String fragment,
                                       @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                  example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                       @RequestParam(name = QueryParams.NAMESPACE,
                                                     required = false) Optional<String> namespace,
                                       @Parameter(description = "Access control record to add.")
                                       @RequestBody AccessControlRecord<?> record) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        vocabularyService.addAccessControlRecords(vocabulary, record);
        LOG.debug("Added access control record to ACL of vocabulary {}.", vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes the specified access control record from the access control list of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Record successfully removed."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @DeleteMapping(value = "/{fragment}/acl/records", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAccessControlRecord(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                     example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                          @PathVariable String fragment,
                                          @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                     example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @Parameter(description = "Access control record to remove.")
                                          @RequestBody AccessControlRecord<?> record) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        vocabularyService.removeAccessControlRecord(vocabulary, record);
        LOG.debug("Removed access control record from ACL of vocabulary {}.", vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates access level of specified access control record in the access control list of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Access level successfully updated."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PutMapping(value = "/{fragment}/acl/records/{recordIdFragment}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAccessControlLevel(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                    example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                         @PathVariable String fragment,
                                         @Parameter(
                                                 description = "Locally unique part of the access control record identifier.")
                                         @PathVariable String recordIdFragment,
                                         @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                    example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                         @RequestParam(name = QueryParams.NAMESPACE,
                                                       required = false) Optional<String> namespace,
                                         @Parameter(description = "Access control record to remove.")
                                         @RequestBody AccessControlRecord<?> record) {
        if (!record.getUri().toString().contains(recordIdFragment)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change record identifier does not match URL.");
        }
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        vocabularyService.updateAccessControlLevel(vocabulary, record);
        LOG.debug("Updated access control record {} from ACL of vocabulary {}.", record, vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the access level of the current user to the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access level."),
            @ApiResponse(responseCode = "404", description = VocabularyControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{fragment}/access-level")
    public AccessLevel getAccessLevel(@Parameter(description = VocabularyControllerDoc.ID_FRAGMENT_DESCRIPTION,
                                                 example = VocabularyControllerDoc.ID_FRAGMENT_EXAMPLE)
                                      @PathVariable String fragment,
                                      @Parameter(description = VocabularyControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                 example = VocabularyControllerDoc.ID_NAMESPACE_EXAMPLE)
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        return vocabularyService.getAccessLevel(vocabulary);
    }

    /**
     * A couple of constants for the {@link VocabularyController} API documentation.
     */
    private static final class VocabularyControllerDoc {
        private static final String ID_FRAGMENT_DESCRIPTION = "Locally (in the context of the specified namespace/default vocabulary namespace) unique part of the vocabulary identifier.";
        private static final String ID_FRAGMENT_EXAMPLE = "datovy-mpp-3.4";
        private static final String ID_NAMESPACE_DESCRIPTION = "Identifier namespace. Allows to override the default vocabulary identifier namespace.";
        private static final String ID_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/slovnik/";
        private static final String ID_NOT_FOUND_DESCRIPTION = "Vocabulary with the specified identifier not found.";
    }
}
