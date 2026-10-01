package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.changetracking.ChangeRollbackService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
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
@RequestMapping("/")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class RollbackController extends BaseController {
    private static final Logger LOG = LoggerFactory.getLogger(RollbackController.class);

    private final ChangeRollbackService changeRollbackService;

    @Autowired
    public RollbackController(IdentifierResolver idResolver, Configuration config,
                              ChangeRollbackService changeRollbackService) {
        super(idResolver, config);
        this.changeRollbackService = changeRollbackService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Rolls back the specified update change record of the vocabulary.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Change successfully rolled back."),
            @ApiResponse(responseCode = "404", description = "Vocabulary or update change record not found."),
            @ApiResponse(responseCode = "422", description = "When the change record is associated with a different asset")
    })
    @PostMapping("/vocabularies/{localName}/history/{changeRecord}/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rollbackVocabulary(@Parameter(description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                                              example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                                   @PathVariable String localName,
                                   @Parameter(description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                                              example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                                   @RequestParam(name = QueryParams.NAMESPACE) String namespace,
                                   @Parameter(description = "Local name of the update change record to roll back.")
                                   @PathVariable String changeRecord) {
        final URI vocabularyUri = resolveIdentifier(namespace, localName);
        rollback(vocabularyUri, changeRecord, "Vocabulary");
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Rolls back the specified update change record of the term.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Change successfully rolled back."),
            @ApiResponse(responseCode = "404", description = "Term or update change record not found."),
            @ApiResponse(responseCode = "422", description = "When the change record is associated with a different asset")
    })
    @PostMapping("/terms/{localName}/history/{changeRecord}/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rollbackTerm(
            @Parameter(description = TermController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION, example = TermController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @PathVariable String changeRecord,
            @Parameter(description = TermController.ApiDoc.ID_NAMESPACE_DESCRIPTION, example = TermController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = QueryParams.NAMESPACE) String namespace) {
        final URI termUri = resolveIdentifier(namespace, localName);
        rollback(termUri, changeRecord, "Term");
    }

    private void rollback(URI entityUri, String changeRecord, String entityType) {
        final UpdateChangeRecord record = changeRollbackService.findRecordByLocalName(changeRecord);
        if (!entityUri.equals(record.getChangedEntity())) {
            throw new InvalidParameterException("Record not associated with specified " + entityType);
        }
        changeRollbackService.rollback(record);
        LOG.debug("Change record {} of {} <{}> rolled back.", changeRecord, entityType.toLowerCase(), entityUri);
    }
}
