package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.service.business.PasswordChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@ConditionalOnProperty(prefix = "termit.security", name = "provider", havingValue = "internal", matchIfMissing = true)
@Tag(name = "Password reset", description = "User forgotten password reset API")
@RestController
@RequestMapping("/password")
public class PasswordChangeController {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordChangeController.class);

    private final PasswordChangeService passwordChangeService;

    @Autowired
    public PasswordChangeController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    @Operation(description = "Requests a password reset for the specified username.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password reset request accepted, email sent"),
            @ApiResponse(responseCode = "404", description = "User with the specified username not found.")
    })
    @PreAuthorize("permitAll()")
    @PostMapping(consumes = {MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Void> requestPasswordReset(
            @Parameter(description = "Username of the user") @RequestBody String username) {
        LOG.info("Password reset requested for user {}.", username);
        passwordChangeService.requestPasswordReset(username);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(description = "Changes the password for the specified user.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "409", description = "Invalid or expired token")
    })
    @PreAuthorize("permitAll()")
    @PutMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> changePassword(
            @Parameter(
                    description = "Token with URI for password reset") @RequestBody PasswordChangeDto passwordChangeDto) {
        LOG.info("Password change requested with token {}", passwordChangeDto.getToken());
        passwordChangeService.changePassword(passwordChangeDto);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
