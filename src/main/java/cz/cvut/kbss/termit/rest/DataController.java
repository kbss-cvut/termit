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
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "Data", description = "Extra-domain data access API")
@RestController
@RequestMapping("/data")
public class DataController {

    private static final Logger LOG = LoggerFactory.getLogger(DataController.class);

    private final DataRepositoryService dataService;

    @Autowired
    public DataController(DataRepositoryService dataService) {
        this.dataService = dataService;
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
        dataService.persistProperty(property);
        LOG.debug("Created property {}.", property);
        return ResponseEntity.created(RestUtils.createLocationFromCurrentUri()).build();
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
