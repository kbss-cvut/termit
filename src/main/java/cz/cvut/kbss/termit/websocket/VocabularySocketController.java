package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.rest.BaseController;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cz.cvut.kbss.termit.websocket.ResultWithHeaders.result;

@Controller
@MessageMapping("/vocabularies")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class VocabularySocketController extends BaseController {

    private final VocabularyService vocabularyService;

    protected VocabularySocketController(IdentifierResolver idResolver, Configuration config,
                                         VocabularyService vocabularyService) {
        super(idResolver, config);
        this.vocabularyService = vocabularyService;
    }

    /**
     * Validates the terms in a vocabulary with the specified identifier.
     */
    @MessageMapping("/{localName}/validate")
    public ResultWithHeaders<List<ValidationResult>> validateVocabulary(@DestinationVariable String localName,
                                                                        @Header(name = Constants.QueryParams.NAMESPACE,
                                                                                required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);
        return result(vocabularyService.validateContents(vocabulary)).withHeaders(Map.of("vocabulary", identifier))
                                                                     .sendToUser("/vocabularies/validation");
    }
}
