package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.CustomAttributesProfile;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.CustomAttributeProfileService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Tag(name = "Custom attribute profiles", description = "Management API for custom attribute profiles")
@RestController
@RequestMapping(CustomAttributeProfileController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
public class CustomAttributeProfileController {
    static final String PATH = "/data/profile/custom-attributes";

    private static final Logger LOG = LoggerFactory.getLogger(CustomAttributeProfileController.class);

    private final CustomAttributeProfileService profileService;

    @Autowired
    public CustomAttributeProfileController(CustomAttributeProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description = "Imports custom attributes from the specified JSON-LD profile. "
                    + "Attributes which already exist are updated with the imported data.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Custom attributes successfully imported."),
        @ApiResponse(
                responseCode = "400",
                description =
                        "The profile is missing its identifier, title, custom attributes, or the provided JSON is invalid."),
        @ApiResponse(
                responseCode = "409",
                description = "The profile or its custom attributes are invalid."
                        + " An existing attribute conflicts with definition in the profile."),
    })
    @PostMapping(value = "/import", consumes = JsonLd.MEDIA_TYPE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void importCustomAttributes(
            @Parameter(description = "Custom attribute profile to import.") @Valid @RequestBody
                    CustomAttributesProfile profile) {
        LOG.debug(
                "Importing {} custom attribute(s) from profile <{}>.",
                profile.getCustomAttributes().size(),
                profile.getUri());
        profileService.importProfile(profile);
    }
}
