package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.IriMigrationService;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Migration", description = "Migration API")
@RestController
@RequestMapping(MigrationController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class MigrationController {

    public static final String PATH = "/migrate";

    private final IriMigrationService iriMigrationService;

    @Autowired
    public MigrationController(IriMigrationService iriMigrationService) {
        this.iriMigrationService = iriMigrationService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Migrates the identifier of a vocabulary, term or custom attribute to a new one, " +
                       "replacing all occurrences of the original identifier. The migration runs asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Identifier migration started."),
            @ApiResponse(responseCode = "403", description = "Not authorized to modify the migrated asset."),
            @ApiResponse(responseCode = "404",
                         description = "Asset with the original identifier and the specified type not found."),
            @ApiResponse(responseCode = "400", description = "Invalid migration parameters supplied.")
    })
    @PostMapping("/identifier")
    public ResponseEntity<Void> migrateIdentifier(
            @Parameter(description = "Identifier which should be replaced.",
                       example = MigrationControllerDoc.ORIGINAL_IRI_EXAMPLE)
            @RequestParam(name = "originalIri") URI originalIri,
            @Parameter(description = "Identifier which should replace the original one.",
                       example = MigrationControllerDoc.NEW_IRI_EXAMPLE)
            @RequestParam(name = "newIri") URI newIri,
            @Parameter(description = "Type of the entity whose identifier is being migrated.")
            @RequestParam(name = "type") IriMigrationType migrationType,
            @Parameter(description = "Namespace to set as the preferred namespace of the migrated vocabulary. " +
                    "Identifiers of the vocabulary terms are migrated to this namespace as well. " +
                    "Applicable only when migrating a vocabulary identifier.",
                       example = MigrationControllerDoc.PREFERRED_NAMESPACE_EXAMPLE)
            @RequestParam(name = "preferredNamespace", required = false) URI preferredNamespace) {
        final IriMigrationPair iris = new IriMigrationPair(originalIri, newIri);
        iriMigrationService.migrateIdentifier(iris, migrationType, new IriMigrationParams(preferredNamespace));
        return ResponseEntity.accepted().build();
    }

    private static final class MigrationControllerDoc {
        private static final String ORIGINAL_IRI_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/slovnik/original-vocabulary";
        private static final String NEW_IRI_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/slovnik/new-vocabulary";
        private static final String PREFERRED_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/slovnik/new-vocabulary/pojem/";
    }
}
