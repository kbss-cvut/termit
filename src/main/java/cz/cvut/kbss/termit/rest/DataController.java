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
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.rest.doc.ApiDocConstants;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.eclipse.rdf4j.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

import java.net.URI;
import java.util.List;

@Tag(name = "Data", description = "Extra-domain data access API")
@RestController
@RequestMapping("/data")
public class DataController {

    private static final Logger LOG = LoggerFactory.getLogger(DataController.class);

    private final DataRepositoryService dataService;
    private final IdentifierResolver identifierResolver;

    @Autowired
    public DataController(DataRepositoryService dataService, IdentifierResolver identifierResolver) {
        this.dataService = dataService;
        this.identifierResolver = identifierResolver;
    }

    @Operation(description = "Gets all unique RDF properties used by the data in the system.")
    @ApiResponse(responseCode = "200", description = "List of RDFS resources representing properties.")
    @GetMapping(value = "/properties", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RdfsResource> getProperties() {
        return dataService.findAllProperties();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new unmapped RDF property in the repository.")
    @ApiResponse(responseCode = "201", description = "Property successfully created.")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    @PostMapping(value = "/properties", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createProperty(@Parameter(description = "Property metadata.")
                                               @RequestBody RdfsResource property) {
        dataService.persist(property);
        LOG.debug("Created property {}.", property);
        return ResponseEntity.created(RestUtils.createLocationFromCurrentUri()).build();
    }

    @Operation(description = "Gets all user-defined custom attributes in the system.")
    @ApiResponse(responseCode = "200", description = "List of custom attributes.")
    @GetMapping(value = "/custom-attributes", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<CustomAttribute> getCustomAttributes() {
        return dataService.findAllCustomAttributes();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new custom attribute.")
    @ApiResponse(responseCode = "201", description = "Attribute successfully created.")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PostMapping(value = "/custom-attributes", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createCustomAttribute(@Parameter(description = "Attribute metadata.")
                                                      @RequestBody CustomAttribute attribute) {
        dataService.persistCustomAttribute(attribute);
        LOG.debug("Created custom attribute {}.", attribute);
        return ResponseEntity.created(RestUtils.createLocationFromCurrentUri()).build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates a custom attribute. Only label and description can be changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Attribute successfully updated."),
            @ApiResponse(responseCode = "404", description = "Attribute not found.")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PutMapping(value = "/custom-attributes/{localName}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public void updateCustomAttribute(@Parameter(
                                              description = "Locally (in the context of the custom attributes namespace) unique part of the attribute identifier.",
                                              example = "custom-attribute")
                                      @PathVariable String localName,
                                      @Parameter(description = "Updated attribute metadata.")
                                      @RequestBody CustomAttribute update) {
        dataService.updateCustomAttribute(update);
        LOG.debug("Updated custom attribute {}.", update);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Retrieves the triples where the specified custom attribute is used as predicate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The list of triples with the custom attribute as predicate"),
            @ApiResponse(responseCode = "404", description = "Attribute not found")
    })
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @GetMapping(value = "/custom-attributes/{localName}/usage", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<List<Statement>> getCustomAttributeUsage(@Parameter(
                                                                           description = "Locally (in the context of the namespace) unique part of the attribute identifier.",
                                                                           example = "custom-attribute")
                                                                   @PathVariable String localName,
                                                                   @Parameter(
                                                                           description = "Custom attribute identifier namespace",
                                                                           example = "http://onto.fel.cvut.cz/ontologies/application/termit/custom-attribute/"
                                                                   )
                                                                   @RequestParam String namespace,
                                                                   @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
                                                                   @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false) Integer pageSize,
                                                                   @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
                                                                   @RequestParam(name = Constants.QueryParams.PAGE, required = false) Integer pageNo) {
        Pageable pageable = Constants.DEFAULT_PAGE_SPEC;
        if (pageSize != null && pageNo != null) {
            pageable = PageRequest.of(pageNo, pageSize);
        }
        final URI identifier = identifierResolver.resolveIdentifier(namespace, localName);
        final Page<Statement> result = dataService.findCustomAttributeUsage(identifier, pageable);
        return ResponseEntity.ok()
                .header(Constants.X_TOTAL_COUNT_HEADER, Long.toString(result.getTotalElements()))
                .body(result.getContent());
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes the custom attribute, " +
                       "if the removeUsages is not enabled, the usages of the attribute wont be removed.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Custom attribute was removed"),
            @ApiResponse(responseCode = "404", description = "Attribute not found")
    })
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(value = "/custom-attributes/{localName}")
    public void removeCustomAttribute(@Parameter(
                                              description = "Locally (in the context of the namespace) unique part of the attribute identifier.",
                                              example = "custom-attribute")
                                      @PathVariable String localName,
                                      @Parameter(
                                              description = "Custom attribute identifier namespace",
                                              example = "http://onto.fel.cvut.cz/ontologies/application/termit/custom-attribute/"
                                      )
                                      @RequestParam String namespace,
                                      @Parameter(
                                              description = "Indicates whether to remove the usages of the custom attribute as well" +
                                                      "When false, only the attribute itself will be removed, its usages will be kept."
                                      )
                                      @RequestParam boolean removeUsages) {
        final URI identifier = identifierResolver.resolveIdentifier(namespace, localName);
        dataService.removeCustomAttribute(identifier, removeUsages);
        LOG.debug("Removed custom attribute: {}", identifier);
    }

    @Operation(description = "Gets basic metadata for a RDFS resource with the specified IRI.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "RDSF resource metadata."),
            @ApiResponse(responseCode = "404", description = "Resource not found.")
    })
    @GetMapping(value = "/resource", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public RdfsResource getById(@Parameter(description = "Identifier of the resource to retrieve.")
                                @RequestParam("iri") URI id) {
        return dataService.find(id).orElseThrow(() -> NotFoundException.create("Resource", id));
    }

    @Operation(
            description = "Gets the label of a RDFS resource with the specified IRI. " +
                    "Unless a specific language is requested, the label is in the vocabulary language when available, " +
                    "otherwise the configured persistence unit language is used instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "RDFS resource label."),
            @ApiResponse(responseCode = "404", description = "Resource not found.")
    })
    @GetMapping(value = "/label")
    public String getLabel(@Parameter(description = "Resource identifier.")
                           @RequestParam("iri") URI id,
                           @Parameter(description = "Label language")
                           @RequestParam(value = "language", required = false) String language
    ) {
        return dataService.getLabel(id, language).orElseThrow(
                () -> new NotFoundException("Resource with id " + id + " not found or it has no matching label."));
    }
}
