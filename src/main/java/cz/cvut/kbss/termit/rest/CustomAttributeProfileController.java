package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.CustomAttributesProfile;
import cz.cvut.kbss.termit.dto.CustomAttributesProfileDescription;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.CustomAttributeProfileService;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

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
                description = "The profile is missing its identifier, title or custom attributes."),
        @ApiResponse(responseCode = "409", description = "The profile is invalid."),
        @ApiResponse(responseCode = "422", description = "Existing attribute conflicts with definition in the profile.")
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

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description = "Exports the specified custom attributes as a JSON-LD profile file. The profile "
                    + "description is passed as JSON-LD in the request body.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "JSON-LD file with the custom attribute profile is returned as attachment."),
        @ApiResponse(
                responseCode = "400",
                description = "Profile description is invalid or no custom attribute identifiers were provided.")
    })
    // TODO: replace with QUERY mapping after spring boot 4.2
    @PostMapping(value = "/export", consumes = JsonLd.MEDIA_TYPE, produces = JsonLd.MEDIA_TYPE)
    public ResponseEntity<byte[]> exportCustomAttributes(
            @Parameter(description = "Description of the exported profile - its identifier, title and description.")
                    @Valid
                    @RequestBody
                    CustomAttributesProfileDescription profileDescription,
            @Parameter(description = "Identifiers of custom attributes to include in the profile.")
                    @RequestParam(name = "customAttributeUris")
                    @NotEmpty
                    Set<URI> customAttributeUris)
            throws IOException {
        LOG.debug(
                "Exporting profile <{}> with {} custom attribute(s).",
                profileDescription.getUri(),
                customAttributeUris.size());
        final TypeAwareResource profile = profileService.exportProfile(profileDescription, customAttributeUris);
        final String title = IdentifierResolver.sanitizeFileName(
                IdentifierResolver.extractIdentifierFragment(profileDescription.getUri()));
        final String filename = title + profile.getFileExtension().orElse("");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(profile.getMediaType().orElse(JsonLd.MEDIA_TYPE)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(profile.getContentAsByteArray());
    }
}
