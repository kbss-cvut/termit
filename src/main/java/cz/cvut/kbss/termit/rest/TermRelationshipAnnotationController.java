package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.meta.AnnotatedTermRelationship;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.readonly.TermRelationshipAnnotationService;
import cz.cvut.kbss.termit.util.Configuration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "Term relationship annotations",
     description = "Annotations of (assigning data to) relationships between terms")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
@RestController
public class TermRelationshipAnnotationController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(TermRelationshipAnnotationController.class);

    private final TermRelationshipAnnotationService service;

    public TermRelationshipAnnotationController(IdentifierResolver idResolver, Configuration config,
                                                TermRelationshipAnnotationService service) {
        super(idResolver, config);
        this.service = service;
    }

    @Operation(security = @SecurityRequirement(name = "bearer-key"),
               description = "Gets annotations of relationships of the specified term")
    @ApiResponse(responseCode = "200", description = "List of term relationship annotations.")
    @ApiResponse(responseCode = "404", description = TermController.ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    @GetMapping(value = "/terms/{localName}/relationship-annotations",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermRelationshipAnnotation> getRelationshipAnnotations(
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam String namespace) {
        final URI termUri = resolveIdentifier(namespace, localName);
        return service.findAllForSubject(termUri);
    }

    @Operation(security = @SecurityRequirement(name = "bearer-key"),
               description = "Gets info about term relationships that are annotated by the specified term.")
    @ApiResponse(responseCode = "200", description = "List of annotated term relationships.")
    @ApiResponse(responseCode = "404", description = TermController.ApiDoc.ID_STANDALONE_NOT_FOUND_DESCRIPTION)
    @GetMapping(value = "/terms/{localName}/annotated-relationships",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AnnotatedTermRelationship> getAnnotatedRelationships(
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam String namespace) {
        final URI termUri = resolveIdentifier(namespace, localName);
        return service.findAnnotatedRelationships(termUri);
    }

    @Operation(security = @SecurityRequirement(name = "bearer-key"),
               description = "Updates annotations of a single relationship of the specified term. " +
                       "Other existing term relationship annotations are preserved. " +
                       "To remove all annotations of the specified relationship, send the annotation with empty values.")
    @PatchMapping(value = "/terms/{localName}/relationship-annotations",
                  consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRelationshipAnnotation(
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_LOCAL_NAME_DESCRIPTION,
                       example = TermController.ApiDoc.ID_TERM_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_DESCRIPTION,
                       example = TermController.ApiDoc.ID_STANDALONE_NAMESPACE_EXAMPLE)
            @RequestParam String namespace,
            @Parameter(description = "Term relationship annotation")
            @RequestBody TermRelationshipAnnotation annotation) {
        final URI termUri = resolveIdentifier(namespace, localName);
        service.updateAnnotation(termUri, annotation);
        LOG.debug("Successfully updated annotation of term relationship {}.", annotation.getRelationship());
    }
}
