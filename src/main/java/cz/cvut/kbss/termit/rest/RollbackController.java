package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.changetracking.ChangeRollbackService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Rollback", description = "Change rollback API")
@RestController
@RequestMapping("/history")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class RollbackController extends BaseController {
    private static final Logger LOG = LoggerFactory.getLogger(RollbackController.class);

    private final ChangeRollbackService changeRollbackService;

    @Autowired
    public RollbackController(
            IdentifierResolver idResolver, Configuration config, ChangeRollbackService changeRollbackService) {
        super(idResolver, config);
        this.changeRollbackService = changeRollbackService;
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description = "Rolls back the specified update change record.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Change successfully rolled back."),
        @ApiResponse(responseCode = "404", description = "Update change record not found."),
    })
    @PostMapping("/{localName}/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rollback(
            @Parameter(description = "Local name of the update change record to roll back.") @PathVariable
                    String localName,
            @Parameter(description = "Change record identifier namespace")
                    @RequestParam(name = Constants.QueryParams.NAMESPACE)
                    String namespace) {
        final URI recordUri = resolveIdentifier(namespace, localName);
        final UpdateChangeRecord record = changeRollbackService.findUpdateRecord(recordUri);
        changeRollbackService.rollback(record);
        LOG.debug("Change record <{}> rolled back.", record);
    }
}
