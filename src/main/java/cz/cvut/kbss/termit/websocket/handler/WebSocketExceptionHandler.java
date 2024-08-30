package cz.cvut.kbss.termit.websocket.handler;

import cz.cvut.kbss.jopa.exceptions.EntityNotFoundException;
import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jsonld.exception.JsonLdException;
import cz.cvut.kbss.termit.exception.AnnotationGenerationException;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.InvalidLanguageConstantException;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeRequestException;
import cz.cvut.kbss.termit.exception.InvalidTermStateException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.SnapshotNotEditableException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.UnsupportedSearchFacetException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.rest.handler.RestExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Component
@ControllerAdvice
public class WebSocketExceptionHandler extends RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

    @MessageExceptionHandler
    public void messageDeliveryException(Message<?> message, MessageDeliveryException e) {
        final StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
        LOG.error("Failed to send message with destination {}: {}", headerAccessor.getDestination(), e.getMessage());
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> persistenceException(HttpServletRequest request, PersistenceException e) {
        return super.persistenceException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> jopaException(HttpServletRequest request, OWLPersistenceException e) {
        return super.jopaException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> resourceExistsException(HttpServletRequest request, ResourceExistsException e) {
        return super.resourceExistsException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> resourceNotFound(HttpServletRequest request, NotFoundException e) {
        return super.resourceNotFound(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> usernameNotFound(HttpServletRequest request, UsernameNotFoundException e) {
        return super.usernameNotFound(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> entityNotFoundException(HttpServletRequest request, EntityNotFoundException e) {
        return super.entityNotFoundException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> authorizationException(HttpServletRequest request, AuthorizationException e) {
        return super.authorizationException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> validationException(HttpServletRequest request, ValidationException e) {
        return super.validationException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> webServiceIntegrationException(HttpServletRequest request,
                                                                    WebServiceIntegrationException e) {
        return super.webServiceIntegrationException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> annotationGenerationException(HttpServletRequest request,
                                                                   AnnotationGenerationException e) {
        return super.annotationGenerationException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> termItException(HttpServletRequest request, TermItException e) {
        return super.termItException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> jsonLdException(HttpServletRequest request, JsonLdException e) {
        return super.jsonLdException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> unsupportedAssetOperationException(HttpServletRequest request,
                                                                        UnsupportedOperationException e) {
        return super.unsupportedAssetOperationException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> vocabularyImportException(HttpServletRequest request,
                                                               VocabularyImportException e) {
        return super.vocabularyImportException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> unsupportedImportMediaTypeException(HttpServletRequest request,
                                                                         UnsupportedImportMediaTypeException e) {
        return super.unsupportedImportMediaTypeException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> assetRemovalException(HttpServletRequest request, AssetRemovalException e) {
        return super.assetRemovalException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> invalidParameter(HttpServletRequest request, InvalidParameterException e) {
        return super.invalidParameter(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> maxUploadSizeExceededException(HttpServletRequest request,
                                                                    MaxUploadSizeExceededException e) {
        return super.maxUploadSizeExceededException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> snapshotNotEditableException(HttpServletRequest request,
                                                                  SnapshotNotEditableException e) {
        return super.snapshotNotEditableException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> unsupportedSearchFacetException(HttpServletRequest request,
                                                                     UnsupportedSearchFacetException e) {
        return super.unsupportedSearchFacetException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> invalidLanguageConstantException(HttpServletRequest request,
                                                                      InvalidLanguageConstantException e) {
        return super.invalidLanguageConstantException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> invalidTermStateException(HttpServletRequest request,
                                                               InvalidTermStateException e) {
        return super.invalidTermStateException(request, e);
    }

    @Override
    @MessageExceptionHandler
    public ResponseEntity<ErrorInfo> invalidPasswordChangeRequestException(HttpServletRequest request,
                                                                           InvalidPasswordChangeRequestException e) {
        return super.invalidPasswordChangeRequestException(request, e);
    }
}
