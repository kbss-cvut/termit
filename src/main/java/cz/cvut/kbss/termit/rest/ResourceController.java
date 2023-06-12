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
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.ResourceService;
import cz.cvut.kbss.termit.util.Configuration;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Tag(name = "Resources", description = "Resource management API")
@RestController
@RequestMapping("/resources")
public class ResourceController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceController.class);

    private final ResourceService resourceService;

    @Autowired
    public ResourceController(IdentifierResolver idResolver, Configuration config, ResourceService resourceService) {
        super(idResolver, config);
        this.resourceService = resourceService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets detail of the resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching resource metadata."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Resource getResource(
            @Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(resourceNamespace(namespace), localName);
        return resourceService.findRequired(identifier);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Resource successfully updated."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Resource metadata are invalid.")
    })
    @PutMapping(value = "/{localName}", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateResource(@Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                          example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                               @PathVariable String localName,
                               @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                          example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
                               @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
                               @Parameter(description = "Updated resource metadata.")
                               @RequestBody Resource resource) {
        final URI identifier = resolveIdentifier(resourceNamespace(namespace), localName);
        verifyRequestAndEntityIdentifier(resource, identifier);
        resourceService.update(resource);
        LOG.debug("Resource {} updated.", resource);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the content of the resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resource content."),
            @ApiResponse(responseCode = "404", description = "Resource not found or its content is not stored.")
    })
    @GetMapping(value = "/{localName}/content")
    public ResponseEntity<org.springframework.core.io.Resource> getContent(
            @Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(description = "Whether to return the content as a file attachment or directly in response body.")
            @RequestParam(name = "attachment", required = false) boolean asAttachment,
            @Parameter(
                    description = "Datetime (ISO-format) at which the content is expected to be valid. Allows getting older revisions of the resource content.")
            @RequestParam(name = "at", required = false) Optional<String> at) {
        final Resource resource = getResource(localName, namespace);
        try {
            final TypeAwareResource content;
            if (at.isPresent()) {
                final Instant timestamp = RestUtils.parseTimestamp(at.get());
                content = resourceService.getContent(resource, timestamp);
            } else {
                content = resourceService.getContent(resource);
            }
            final ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                                                                     .contentLength(content.contentLength())
                                                                     .contentType(MediaType.parseMediaType(
                                                                             content.getMediaType()
                                                                                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)));
            if (asAttachment) {
                builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + localName + "\"");
            }
            return builder.body(content);
        } catch (IOException e) {
            throw new TermItException("Unable to load content of resource " + resource, e);
        }
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Saves new content of the resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Content successfully saved."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @PutMapping(value = "/{localName}/content")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveContent(@Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                       example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                            @PathVariable String localName,
                            @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                       example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
                            @RequestParam(name = QueryParams.NAMESPACE, required = false) Optional<String> namespace,
                            @Parameter(description = "File with the new content.")
                            @RequestParam(name = "file") MultipartFile attachment) {
        final Resource resource = getResource(localName, namespace);
        try {
            resourceService.saveContent(resource, attachment.getInputStream());
        } catch (IOException e) {
            throw new TermItException(
                    "Unable to read file (fileName=\"" + attachment.getOriginalFilename() + "\") content from request.",
                    e);
        }
        LOG.debug("Content saved for resource {}.", resource);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Checks whether content is stored for the resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "Content is stored. Content-Type header returns stored content type."),
            @ApiResponse(responseCode = "404", description = "Resource not found or its content is not stored.")
    })
    @RequestMapping(value = "/{localName}/content", method = RequestMethod.HEAD)
    public ResponseEntity<Void> hasContent(@Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                      example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                                           @PathVariable String localName,
                                           @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                      example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
                                           @RequestParam(name = QueryParams.NAMESPACE,
                                                         required = false) Optional<String> namespace) {
        final Resource r = getResource(localName, namespace);
        final boolean hasContent = resourceService.hasContent(r);
        if (!hasContent) {
            return ResponseEntity.notFound().build();
        } else {
            final String contentType = resourceService.getContent(r).getMediaType().orElse(null);
            return ResponseEntity.noContent().header(HttpHeaders.CONTENT_TYPE, contentType).build();
        }
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets files associated with the document with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document files."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Resource is not a document.")
    })
    @GetMapping(value = "/{localName}/files", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<File> getFiles(@Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                          example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                               @PathVariable String localName,
                               @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                          example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
                               @RequestParam(name = QueryParams.NAMESPACE,
                                             required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getResource()), localName);
        return resourceService.getFiles(resourceService.getRequiredReference(identifier));
    }

    private String resourceNamespace(Optional<String> namespace) {
        return namespace.orElse(config.getNamespace().getResource());
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Adds a file to the document with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File added."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Resource is not a document.")
    })
    @PostMapping(value = "/{localName}/files", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> addFileToDocument(
            @Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace,
            @Parameter(description = "File to add to the document.")
            @RequestBody File file) {
        final URI identifier = resolveIdentifier(resourceNamespace(namespace), localName);
        resourceService.addFileToDocument(resourceService.findRequired(identifier), file);
        LOG.debug("File {} successfully added to document {}.", file, identifier);
        return ResponseEntity.created(createFileLocation(file.getUri(), localName)).build();
    }

    private URI createFileLocation(URI childUri, String parentIdFragment) {
        final String u = generateLocation(childUri, config.getNamespace().getResource()).toString();
        return URI.create(u.replace("/" + parentIdFragment + "/files", ""));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes a file from the document with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "File removed."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Resource is not a document.")
    })
    @DeleteMapping(value = "/{resourceLocalName}/files/{fileName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFileFromDocument(@Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                  example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                                       @PathVariable String resourceLocalName,
                                       @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                  example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
                                       @RequestParam(name = QueryParams.NAMESPACE,
                                                     required = false) Optional<String> namespace,
                                       @Parameter(
                                               description = "Local (in the context of the parent document identifier) name of the file to remove.")
                                       @PathVariable String fileName) {
        final URI fileIdentifier = resolveIdentifier(resourceNamespace(namespace), fileName);
        final File file = (File) resourceService.findRequired(fileIdentifier);
        resourceService.removeFile(file);
        LOG.debug("File {} successfully removed.", fileIdentifier);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Runs text analysis on the content of the resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Text analysis executed."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION),
            @ApiResponse(responseCode = "409", description = "Resource has no content to analyze.")
    })
    @PutMapping(value = "/{localName}/text-analysis")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void runTextAnalysis(@Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                           example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                                @PathVariable String localName,
                                @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                           example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
                                @RequestParam(name = QueryParams.NAMESPACE,
                                              required = false) Optional<String> namespace,
                                @Parameter(
                                        description = "Identifiers of vocabularies whose terms are used to seed text analysis.")
                                @RequestParam(name = "vocabulary", required = false,
                                              defaultValue = "") Set<URI> vocabularies) {
        final Resource resource = getResource(localName, namespace);
        resourceService.runTextAnalysis(resource, vocabularies);
        LOG.debug("Text analysis finished for resource {}.", resource);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the result of the latest text analysis of the content of the resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Text analysis record."),
            @ApiResponse(responseCode = "404",
                         description = "Resource not found or no text analysis record exists for it."),
    })
    @GetMapping(value = "/{localName}/text-analysis/records/latest", produces = {MediaType.APPLICATION_JSON_VALUE,
                                                                                 JsonLd.MEDIA_TYPE})
    public TextAnalysisRecord getLatestTextAnalysisRecord(
            @Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final Resource resource = getResource(localName, namespace);
        return resourceService.findLatestTextAnalysisRecord(resource);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of changes made to metadata of the resource with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of change records."),
            @ApiResponse(responseCode = "404", description = ResourceControllerDoc.ID_NOT_FOUND_DESCRIPTION)

    })
    @GetMapping(value = "/{localName}/history", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(
            @Parameter(description = ResourceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = ResourceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = ResourceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = ResourceControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE,
                          required = false) Optional<String> namespace) {
        final Resource resource = resourceService
                .getRequiredReference(resolveIdentifier(resourceNamespace(namespace), localName));
        return resourceService.getChanges(resource);
    }

    /**
     * A couple of constants for the {@link ResourceController} API documentation.
     */
    private static final class ResourceControllerDoc {
        private static final String ID_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace/default resource namespace) unique part of the resource identifier.";
        private static final String ID_LOCAL_NAME_EXAMPLE = "mpp-draft.html";
        private static final String ID_NAMESPACE_DESCRIPTION = "Identifier namespace. Allows to override the default resource identifier namespace.";
        private static final String ID_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/zdroj/";
        private static final String ID_NOT_FOUND_DESCRIPTION = "Resource with the specified identifier not found.";
    }
}
