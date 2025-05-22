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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.rest.doc.ApiDocConstants;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.rest.util.RestUtils.createPageRequest;
import java.net.URISyntaxException;
import java.util.logging.Level;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.springframework.web.bind.annotation.RequestPart;

/**
 * Vocabulary management REST API.
 * <p>
 * Note that most endpoints are now secured only by requiring the user to be authenticated, authorization is done on
 * service level based on ACL.
 */
@Tag(name = "Vocabularies", description = "Vocabulary management API")
@RestController
@RequestMapping("/vocabularies")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class VocabularyController extends BaseController {
    static final String DEFAULT_PAGE_SIZE = "10";
    static final String DEFAULT_PAGE = "0";

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
    @ApiResponse(responseCode = "200", description = "List of vocabularies ordered by label.")
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
               description = "Gets detail of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching vocabulary metadata."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Vocabulary getById(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                         example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                              @PathVariable String localName,
                              @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                         example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                              @RequestParam(name = QueryParams.NAMESPACE,
                                            required = false) Optional<String> namespace) {
        final URI id = resolveVocabularyUri(localName, namespace);
        return vocabularyService.findRequired(id);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets identifiers of vocabularies imported (including transitive imports) by the vocabulary with the specified identification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection of vocabulary identifiers."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/imports", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getImports(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                 example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                      @PathVariable String localName,
                                      @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                 example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getReference(
                resolveVocabularyUri(localName, namespace));
        return vocabularyService.getTransitivelyImportedVocabularies(vocabulary);
    }
    
    
    @GetMapping(value = "/imports/available", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RdfsResource> getAvailableVocabularies(){
        return vocabularyService.getAvailableVocabularies();
    }
   
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets identifiers of vocabularies whose terms are in a SKOS relationship with terms from the specified vocabulary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection of vocabulary identifiers."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/related", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getRelated(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                 example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                      @PathVariable String localName,
                                      @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                 example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getReference(resolveVocabularyUri(localName, namespace));
        return vocabularyService.getRelatedVocabularies(vocabulary);
    }

    @Operation(security = {
        @SecurityRequirement(name = "bearer-key")},
            description = "Creates a new vocabulary by importing the specified SKOS glossary"
            + " or providing list of vocabulary iris to be downloaded from an external source")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vocabulary successfully created."),
            @ApiResponse(responseCode = "409",
                         description = "If a matching vocabulary already exists or the imported one contains invalid data.")
    })
    @PostMapping("/import")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(
            @Parameter(description = "File containing a SKOS glossary in RDF.")
            @RequestParam(name = "file", required = false) MultipartFile file,
            @Parameter(description = "List of external SKOS vocabulary IRIs.")
            @RequestParam(name = "vocabularyIris", required = false) List<String> vocabularyIris,
            @Parameter(description = "Whether identifiers should be modified to prevent clashes with existing data.")
            @RequestParam(name = "rename", defaultValue = "false", required = false) boolean rename) {

        if (file != null) { // importing from file
            final Vocabulary vocabulary = vocabularyService.importVocabulary(rename, file);
            LOG.debug("New vocabulary {} imported.", vocabulary);
            return ResponseEntity.created(locationWithout(generateLocation(vocabulary.getUri()), "/import")).build();
        } else if (vocabularyIris != null) { // importing externally
            try {
                Vocabulary vocabulary = vocabularyService.importFromExternalUris(vocabularyIris);
                LOG.debug("Vocabulary imported from IRIs: {}", vocabularyIris.toArray());
                return ResponseEntity.created(locationWithout(generateLocation(vocabulary.getUri()), "/import")).build();

            } catch (URISyntaxException | QueryEvaluationException | RepositoryException ex) {
                LOG.error(ex.getMessage());
                return ResponseEntity.accepted().build();
            }
        } else {
            System.out.println("Eles\n");
            return ResponseEntity.badRequest().build(); // ani soubor, ani IRI
        }
    }

    @Operation(description = "Gets a template Excel file that can be used to import terms into TermIt")
    @ApiResponse(responseCode = "200", description = "Template Excel file is returned as attachment")
    @GetMapping("/import/template")
    @PreAuthorize("permitAll()")
    public ResponseEntity<TypeAwareResource> getExcelTemplateFile(
            @Parameter(description = "Whether the file will be used to import only term translations")
            @RequestParam(name = "translationsOnly", required = false,
                          defaultValue = "false") boolean translationsOnly) {
        final TypeAwareResource template =
                translationsOnly ? vocabularyService.getExcelTranslationsImportTemplateFile() :
                vocabularyService.getExcelImportTemplateFile();
        return ResponseEntity.ok()
                             .contentType(MediaType.parseMediaType(
                                     template.getMediaType().orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
                             .header(HttpHeaders.CONTENT_DISPOSITION,
                                     "attachment; filename=\"" + template.getFilename() + "\"")
                             .body(template);
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
    @PostMapping("/{localName}/import")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace,
            @Parameter(
                    description = "File containing a SKOS glossary in RDF or an Excel file with supported structure.")
            @RequestParam(name = "file") MultipartFile file,
            @Parameter(description = "Whether to import only translations of existing terms from the vocabulary.")
            @RequestParam(name = "translationsOnly", required = false,
                          defaultValue = "false") boolean translationsOnly) {
        final URI vocabularyIri = resolveVocabularyUri(localName, namespace);
        final Vocabulary result;
        if (translationsOnly) {
            result = vocabularyService.importTermTranslations(vocabularyIri, file);
            LOG.debug("Translations of terms in vocabulary {} imported.", result);
        } else {
            result = vocabularyService.importVocabulary(vocabularyIri, file);
            LOG.debug("Vocabulary {} re-imported.", result);
        }
        return ResponseEntity.created(locationWithout(generateLocation(result.getUri()), "/import/" + localName))
                             .build();
    }

    private URI resolveVocabularyUri(String fragment, Optional<String> namespace) {
        return resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of changes made to metadata of vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of change records."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)

    })
    @GetMapping(value = "/{localName}/history", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace,
            @Parameter(description = ChangeRecordFilterDto.ApiDoc.CHANGE_TYPE_DESCRIPTION)
            @RequestParam(name = "type", required = false) URI changeType,
            @Parameter(description = ChangeRecordFilterDto.ApiDoc.AUTHOR_NAME_DESCRIPTION)
            @RequestParam(name = "author", required = false, defaultValue = "") String authorName,
            @Parameter(description = ChangeRecordFilterDto.ApiDoc.CHANGED_ATTRIBUTE_DESCRIPTION)
            @RequestParam(name = "attribute", required = false, defaultValue = "") String changedAttributeName) {
        final Vocabulary vocabulary = vocabularyService.getReference(
                resolveVocabularyUri(localName, namespace));
        final ChangeRecordFilterDto filterDto = new ChangeRecordFilterDto(changedAttributeName, authorName, changeType);
        return vocabularyService.getChanges(vocabulary, filterDto);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets summary info about changes made to the content of the vocabulary (term creation, editing).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of aggregated change data."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/history-of-content",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AggregatedChangeInfo> getHistoryOfContent(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getReference(
                resolveVocabularyUri(localName, namespace));
        return vocabularyService.getChangesOfContent(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of changes made to the content of the vocabulary (term creation, editing).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "List of change records."),
                   @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)})
    @GetMapping(value = "/{localName}/history-of-content/detail",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getDetailedHistoryOfContent(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_LOCAL_NAME_EXAMPLE) @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_NAMESPACE_EXAMPLE) @RequestParam(name = QueryParams.NAMESPACE,
                                                                            required = false) Optional<String> namespace,
            @Parameter(description = ChangeRecordFilterDto.ApiDoc.TERM_NAME_DESCRIPTION) @RequestParam(name = "term",
                                                                                                       required = false,
                                                                                                       defaultValue = "") String termName,
            @Parameter(description = ChangeRecordFilterDto.ApiDoc.CHANGE_TYPE_DESCRIPTION) @RequestParam(name = "type",
                                                                                                         required = false) URI changeType,
            @Parameter(description = ChangeRecordFilterDto.ApiDoc.AUTHOR_NAME_DESCRIPTION) @RequestParam(
                    name = "author",
                    required = false,
                    defaultValue = "") String authorName,
            @Parameter(description = ChangeRecordFilterDto.ApiDoc.CHANGED_ATTRIBUTE_DESCRIPTION) @RequestParam(
                    name = "attribute", required = false, defaultValue = "") String changedAttributeName,

            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION) @RequestParam(
                    name = Constants.QueryParams.PAGE_SIZE, required = false,
                    defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION) @RequestParam(
                    name = Constants.QueryParams.PAGE, required = false, defaultValue = DEFAULT_PAGE) Integer pageNo) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        final Vocabulary vocabulary = vocabularyService.getReference(resolveVocabularyUri(localName, namespace));
        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto(termName, changedAttributeName, authorName,
                                                                       changeType);
        return vocabularyService.getDetailedHistoryOfContent(vocabulary, filter, pageReq);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of languages used in the vocabulary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of languages.")
    })
    @GetMapping(value = "/{localName}/languages", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<String> getLanguages(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_LOCAL_NAME_EXAMPLE) @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_NAMESPACE_EXAMPLE) @RequestParam(name = QueryParams.NAMESPACE,
                                                                            required = false) Optional<String> namespace) {
        final URI vocabularyUri = resolveVocabularyUri(localName, namespace);
        return vocabularyService.getLanguages(vocabularyUri);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates metadata of vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vocabulary successfully updated."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Provided metadata are invalid.")
    })
    @PutMapping(value = "/{localName}", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateVocabulary(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                            example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                 @PathVariable String localName,
                                 @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                            example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                 @RequestParam(name = QueryParams.NAMESPACE,
                                               required = false) Optional<String> namespace,
                                 @Parameter(description = "Updated vocabulary data.")
                                 @RequestBody Vocabulary update) {
        final URI vocabularyUri = resolveVocabularyUri(localName, namespace);
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
    @PutMapping(value = "/{localName}/terms/text-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runTextAnalysisOnAllTerms(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                     example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                          @PathVariable String localName,
                                          @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                     example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace) {
        vocabularyService.runTextAnalysisOnAllTerms(resolveVocabularyUri(localName, namespace));
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
     * @param localName vocabulary name
     * @param namespace (optional) vocabulary namespace
     * @see VocabularyService#remove(Vocabulary)  for details.
     */
    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vocabulary successfully removed."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409",
                         description = "Unable to remove vocabulary. E.g., it contains terms, it is referenced by other vocabularies, etc.")
    })
    @DeleteMapping(value = "/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVocabulary(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                            example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                 @PathVariable String localName,
                                 @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                            example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                 @RequestParam(name = QueryParams.NAMESPACE,
                                               required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.findRequired(identifier);
        vocabularyService.remove(vocabulary);
        LOG.debug("Vocabulary {} removed.", vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Returns relations with other vocabularies")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A collection of vocabulary relations"),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION),
    })
    @GetMapping(value = "/{localName}/relations")
    public List<RdfStatement> relations(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                    example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                         @PathVariable String localName,
                                        @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                    example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                         @RequestParam(name = QueryParams.NAMESPACE,
                                                       required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.findRequired(identifier);

        return vocabularyService.getVocabularyRelations(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Returns relations with terms from other vocabularies")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A collection of term relations"),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION),
    })
    @GetMapping(value = "/{localName}/terms/relations")
    public List<RdfStatement> termsRelations(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                         example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                              @PathVariable String localName,
                                             @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                         example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                              @RequestParam(name = QueryParams.NAMESPACE,
                                                            required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.findRequired(identifier);

        return vocabularyService.getTermRelations(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a snapshot of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Snapshot successfully created."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PostMapping("/{localName}/versions")
    public ResponseEntity<Void> createSnapshot(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(
                    description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                    example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
        final Snapshot snapshot = vocabularyService.createSnapshot(vocabulary);
        LOG.debug("Created snapshot of vocabulary {}.", vocabulary);
        return ResponseEntity.created(
                locationWithout(generateLocation(snapshot.getUri()), "/" + localName + "/versions")).build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of snapshots of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "A list of snapshots or a snapshot valid at the requested datetime."),
            @ApiResponse(responseCode = "400", description = "Provided timestamp is invalid."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/versions", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                     example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                          @PathVariable String localName,
                                          @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                     example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @Parameter(
                                                  description = "Timestamp at which the returned version was valid. ISO-formatted datetime.",
                                                  example = ApiDocConstants.DATETIME_EXAMPLE)
                                          @RequestParam(name = "at", required = false) Optional<String> at) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
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
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/acl", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public AccessControlListDto getAccessControlList(
            @Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
        return vocabularyService.getAccessControlList(vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Adds the specified access control record to the access control list of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Record successfully added."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PostMapping(value = "/{localName}/acl/records", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addAccessControlRecord(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                  example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                       @PathVariable String localName,
                                       @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                  example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                       @RequestParam(name = QueryParams.NAMESPACE,
                                                     required = false) Optional<String> namespace,
                                       @Parameter(description = "Access control record to add.")
                                       @RequestBody AccessControlRecord<?> record) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
        vocabularyService.addAccessControlRecords(vocabulary, record);
        LOG.debug("Added access control record to ACL of vocabulary {}.", vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes the specified access control record from the access control list of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Record successfully removed."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @DeleteMapping(value = "/{localName}/acl/records", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAccessControlRecord(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                     example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                          @PathVariable String localName,
                                          @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                     example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @Parameter(description = "Access control record to remove.")
                                          @RequestBody AccessControlRecord<?> record) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
        vocabularyService.removeAccessControlRecord(vocabulary, record);
        LOG.debug("Removed access control record from ACL of vocabulary {}.", vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates access level of specified access control record in the access control list of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Access level successfully updated."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PutMapping(value = "/{localName}/acl/records/{recordLocalName}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAccessControlLevel(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                    example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                         @PathVariable String localName,
                                         @Parameter(
                                                 description = "Locally unique part of the access control record identifier.")
                                         @PathVariable String recordLocalName,
                                         @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                    example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                         @RequestParam(name = QueryParams.NAMESPACE,
                                                       required = false) Optional<String> namespace,
                                         @Parameter(description = "Access control record to remove.")
                                         @RequestBody AccessControlRecord<?> record) {
        if (!record.getUri().toString().contains(recordLocalName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change record identifier does not match URL.");
        }
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
        vocabularyService.updateAccessControlLevel(vocabulary, record);
        LOG.debug("Updated access control record {} from ACL of vocabulary {}.", record, vocabulary);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the access level of the current user to the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access level."),
            @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/access-level")
    public AccessLevel getAccessLevel(@Parameter(description = ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                 example = ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                      @PathVariable String localName,
                                      @Parameter(description = ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                                 example = ApiDoc.ID_NAMESPACE_EXAMPLE)
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
        return vocabularyService.getAccessLevel(vocabulary);
    }

    /**
     * A couple of constants for the {@link VocabularyController} API documentation.
     */
    public static final class ApiDoc {
        public static final String ID_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace/default vocabulary namespace) unique part of the vocabulary identifier.";
        public static final String ID_LOCAL_NAME_EXAMPLE = "datovy-mpp-3.4";
        public static final String ID_NAMESPACE_DESCRIPTION = "Identifier namespace. Allows to override the default vocabulary identifier namespace.";
        public static final String ID_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/slovnik/";
        public static final String ID_NOT_FOUND_DESCRIPTION = "Vocabulary with the specified identifier not found.";

        private ApiDoc() {
            throw new AssertionError();
        }
    }
}
