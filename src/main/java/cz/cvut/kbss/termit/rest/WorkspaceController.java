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
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collection;
import java.util.Set;

@Tag(name = "Workspaces", description = "Workspace management API.")
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
