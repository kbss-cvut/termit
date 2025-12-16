package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.PersonalAccessTokenDto;
import cz.cvut.kbss.termit.model.PersonalAccessToken_;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.PersonalAccessTokenService;
import cz.cvut.kbss.termit.util.Configuration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Tag(name = "Personal Access Tokens", description = "Personal Access Tokens management API")
@RestController
@RequestMapping(UserController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class PersonalAccessTokenController extends BaseController{
    public static final String PATH = UserController.PATH + UserController.CURRENT_USER_PATH + "/tokens";
    private final PersonalAccessTokenService service;

    protected PersonalAccessTokenController(IdentifierResolver idResolver, Configuration config,
                                            PersonalAccessTokenService service) {
        super(idResolver, config);
        this.service = service;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Lists all personal tokens for the current user.")
    @ApiResponse(responseCode = "200", description = "List of personal access tokens.")
    @GetMapping(value = PATH, produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<PersonalAccessTokenDto> listPersonalAccessTokens() {
        return service.findAllForCurrentUser();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new token for the current user.")
    @ApiResponse(responseCode = "200", description = "Token created.")
    @ApiResponse(responseCode = "400", description = "Invalid token parameters supplied.")
    @GetMapping(value = PATH, produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public PersonalAccessTokenDto create(
            @Parameter(description = "Expiration date for the new token.")
            Optional<LocalDate> token) {
        return service.create(token.orElse(null));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Deletes token with the specified identifier from the current user.")
    @ApiResponse(responseCode = "200", description = "Token deleted.")
    @ApiResponse(responseCode = "404", description = "Token not found.")
    @DeleteMapping(PATH + "/{localName}")
    public void delete(
            @Parameter(description = "Local name of the token to delete.")
            @PathVariable String localName) {
        final URI tokenUri = resolveIdentifier(PersonalAccessToken_.entityClassIRI.toString(), localName);
        service.delete(tokenUri);
    }
}
