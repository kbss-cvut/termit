/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.event.FileTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.event.TermDefinitionTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.event.TextAnalysisFailedEvent;
import cz.cvut.kbss.termit.event.VocabularyEvent;
import cz.cvut.kbss.termit.event.VocabularyValidationFinishedEvent;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import jakarta.annotation.Nonnull;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@MessageMapping("/vocabularies")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class VocabularySocketController extends BaseWebSocketController {

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
                                   @Nonnull MessageHeaders messageHeaders) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName);
        final Vocabulary vocabulary = vocabularyService.getReference(identifier);

        final ThrottledFuture<Collection<ValidationResult>> future = vocabularyService.validateContents(vocabulary.getUri());

        future.getNow().ifPresentOrElse(validationResults ->
            // if there is a result present (returned from cache), send it
            sendToSession(
                    WebSocketDestinations.VOCABULARIES_VALIDATION,
                    validationResults,
                    getHeaders(identifier,
                            // results are cached if we received a future result, but the future is not done yet
                            Map.of("cached", !future.isDone())),
                    messageHeaders
            ), () ->
            // otherwise reply will be sent once the future is resolved
            future.then(completedFuture ->
                    completedFuture.getNow().ifPresent(results ->
                sendToSession(
                        WebSocketDestinations.VOCABULARIES_VALIDATION,
                        results,
                        getHeaders(identifier,
                                Map.of("cached", false)),
                        messageHeaders
                )))
        );

    }

    /**
     * Publishes results of validation to users.
     */
    @EventListener
    public void onVocabularyValidationFinished(VocabularyValidationFinishedEvent event) {
        messagingTemplate.convertAndSend(
                WebSocketDestinations.VOCABULARIES_VALIDATION,
                event.getValidationResults(),
                getHeaders(event.getVocabularyIri(), Map.of("cached", false))
        );
    }

    @EventListener
    public void onFileTextAnalysisFinished(FileTextAnalysisFinishedEvent event) {
        messagingTemplate.convertAndSend(
                WebSocketDestinations.VOCABULARIES_TEXT_ANALYSIS_FINISHED_FILE,
                event.getFileUri(),
                getHeaders(event)
        );
    }

    @EventListener
    public void onTextAnalysisFailed(TextAnalysisFailedEvent event) {
        messagingTemplate.convertAndSend(
                WebSocketDestinations.VOCABULARIES_TEXT_ANALYSIS_FAILED,
                event.getException().getMessage(),
                getHeaders(event)
        );
    }

    @EventListener
    public void onTermDefinitionTextAnalysisFinished(TermDefinitionTextAnalysisFinishedEvent event) {
        messagingTemplate.convertAndSend(
                WebSocketDestinations.VOCABULARIES_TEXT_ANALYSIS_FINISHED_TERM_DEFINITION,
                event.getTermUri(),
                getHeaders(event)
        );
    }

    protected @Nonnull Map<String, Object> getHeaders(@Nonnull VocabularyEvent event) {
        return getHeaders(event.getVocabularyIri());
    }

    protected @Nonnull Map<String, Object> getHeaders(@Nonnull URI vocabularyUri) {
        return getHeaders(vocabularyUri, Map.of());
    }

    protected @Nonnull Map<String, Object> getHeaders(@Nonnull URI vocabularyUri, Map<String, Object> headers) {
        final Map<String, Object> headersMap = new HashMap<>(headers);
        headersMap.put("vocabulary", vocabularyUri);
        return headersMap;
    }
}
