package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.event.VocabularyValidationFinished;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.throttle.CachableFuture;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Controller
@MessageMapping("/vocabularies")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class VocabularySocketController extends BaseWebSocketController {

    private static final String DESTINATION_VOCABULARIES_VALIDATION = "/vocabularies/validation";

    private final VocabularyService vocabularyService;

    protected VocabularySocketController(IdentifierResolver idResolver, Configuration config,
                                         SimpMessagingTemplate messagingTemplate, VocabularyService vocabularyService) {
        super(idResolver, config, messagingTemplate);
        this.vocabularyService = vocabularyService;
    }

    /**
     * Validates the terms in a vocabulary with the specified identifier.
     * Immediately responds with a result from the cache, if available.
     */
    @MessageMapping("/{localName}/validate")
    public void validateVocabulary(@DestinationVariable String localName,
                                   @Header(name = Constants.QueryParams.NAMESPACE,
                                           required = false) Optional<String> namespace,
                                   @NotNull MessageHeaders messageHeaders) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);

        final CachableFuture<Collection<ValidationResult>> future = vocabularyService.validateContents(vocabulary.getUri());

        future.getNow().ifPresent(validationResults ->
                sendToSession(
                        DESTINATION_VOCABULARIES_VALIDATION,
                        validationResults,
                        Map.of("vocabulary", identifier,
                                "cached", !future.isDone()),
                        messageHeaders
                ));
    }

    /**
     * Publishes results of validation to users.
     */
    @EventListener
    public void onVocabularyValidationFinished(VocabularyValidationFinished event) {
        messagingTemplate.convertAndSend(
                DESTINATION_VOCABULARIES_VALIDATION,
                event.getValidationResults(),
                Map.of("vocabulary", event.getOriginVocabularyIri(), "cached", false)
        );
    }
}
