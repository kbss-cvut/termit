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
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collection;
import java.util.Set;

@Tag(name = "Workspaces", description = "Workspace management API")
@RestController
@RequestMapping(WorkspaceController.PATH)
public class WorkspaceController {

    public static final String PATH = "/workspace";

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Opens the specified set of vocabulary contexts for editing in the current workspace.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Workspace successfully open.")
    })
    @PutMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void openForEditing(@Parameter(description = "Identifiers of vocabulary contexts to open for editing.")
                               @RequestBody Collection<URI> contexts) {
        workspaceService.openForEditing(contexts);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the set of vocabulary contexts open for editing in the current workspace.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "Set of vocabulary contexts currently open for editing. Returns empty set when all vocabularies are editable.")
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
    public Set<URI> getCurrentWorkspace() {
        return workspaceService.getCurrentlyEditedContexts();
    }
}
