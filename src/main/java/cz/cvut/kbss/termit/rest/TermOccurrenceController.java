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
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Term occurrences", description = "Term occurrence management API")
@RestController
@RequestMapping(TermOccurrenceController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class TermOccurrenceController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(TermOccurrenceController.class);

    /**
     * URL path to this controller's endpoints.
     */
    public static final String PATH = "/occurrence";

    private final TermOccurrenceService occurrenceService;

    public TermOccurrenceController(IdentifierResolver idResolver, Configuration config,
                                    TermOccurrenceService occurrenceService) {
        super(idResolver, config);
        this.occurrenceService = occurrenceService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates an approved occurrence.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Term occurrence created"),
            @ApiResponse(responseCode = "409",
                         description = "The occurrence is not valid, e.g., the term or target asset do not exist")
    })
    @PostMapping(consumes = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void createOccurrence(@Parameter(description = "Term occurrence to persist")
                                 @RequestBody TermOccurrence occurrence) {
        occurrenceService.persist(occurrence);
        LOG.debug("Created term occurrence {}.", occurrence);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Approves a suggested term occurrence with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term occurrence approved."),
            @ApiResponse(responseCode = "404", description = "Term occurrence not found.")
    })
    @PutMapping(value = "/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void approveOccurrence(
            @Parameter(description = TermOccurrenceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = TermOccurrenceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermOccurrenceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = TermOccurrenceControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final URI identifier = idResolver.resolveIdentifier(namespace, localName);

        occurrenceService.approve(occurrenceService.getRequiredReference(identifier));
        LOG.debug("Occurrence with identifier <{}> approved.", identifier);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Rejects a suggested term occurrence with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Term occurrence rejected."),
            @ApiResponse(responseCode = "404", description = "Term occurrence not found.")
    })
    @DeleteMapping(value = "/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void removeOccurrence(@Parameter(description = TermOccurrenceControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                            example = TermOccurrenceControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                                 @PathVariable String localName,
                                 @Parameter(description = TermOccurrenceControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                            example = TermOccurrenceControllerDoc.ID_NAMESPACE_EXAMPLE)
                                 @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final URI identifier = idResolver.resolveIdentifier(namespace, localName);
        occurrenceService.remove(occurrenceService.getRequiredReference(identifier));
        LOG.debug("Occurrence with identifier <{}> removed.", identifier);
    }

    /**
     * A couple of constants for the {@link TermOccurrenceController} API documentation.
     */
    private static final class TermOccurrenceControllerDoc {
        private static final String ID_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace) unique part of the term occurrence identifier.";
        private static final String ID_LOCAL_NAME_EXAMPLE = "instance-12345";
        private static final String ID_NAMESPACE_DESCRIPTION = "Term occurrence identifier namespace.";
        private static final String ID_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/application/termit/pojem/v\u00fdskyt-termu/";
    }
}
