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
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static cz.cvut.kbss.termit.util.Constants.QueryParams.NAMESPACE;

@Tag(name = "Snapshots", description = "Snapshot management API")
@RestController
@RequestMapping(SnapshotController.PATH)
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
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
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
