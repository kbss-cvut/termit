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

import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.SnapshotService;
import cz.cvut.kbss.termit.util.Configuration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import static cz.cvut.kbss.termit.util.Constants.QueryParams.NAMESPACE;

@Tag(name = "Snapshots", description = "Snapshot management API")
@RestController
@RequestMapping(SnapshotController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class SnapshotController extends BaseController {

    public static final String PATH = "/snapshots";

    private final SnapshotService snapshotService;

    public SnapshotController(IdentifierResolver idResolver, Configuration config, SnapshotService snapshotService) {
        super(idResolver, config);
        this.snapshotService = snapshotService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Deletes the specified snapshot.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Snapshot successfully deleted."),
            @ApiResponse(responseCode = "404", description = "Snapshot with the specified identifier not found.")
    })
    @DeleteMapping(value = "/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSnapshot(@Parameter(
            description = "Locally (in the context of the specified namespace) unique part of the snapshot identifier.")
                               @PathVariable String localName,
                               @Parameter(description = "Snapshot identifier namespace.")
                               @RequestParam(name = NAMESPACE) String namespace) {
        final URI id = idResolver.resolveIdentifier(namespace, localName);
        snapshotService.remove(snapshotService.findRequired(id));
    }
}
