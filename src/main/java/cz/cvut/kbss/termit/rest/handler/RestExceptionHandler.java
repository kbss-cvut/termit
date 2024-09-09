/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.rest.handler;

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
import cz.cvut.kbss.termit.exception.SuppressibleLogging;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.UnsupportedSearchFacetException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static cz.cvut.kbss.termit.util.ExceptionUtils.isCausedBy;

/**
 * Exception handlers for REST controllers.
 * <p>
 * The general pattern should be that unless an exception can be handled in a more appropriate place it bubbles up to a
 * REST controller which originally received the request. There, it is caught by this handler, logged and a reasonable
 * error message is returned to the user.
 * @implSpec Should reflect {@link cz.cvut.kbss.termit.websocket.handler.WebSocketExceptionHandler}
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

    private static void logException(TermItException ex, HttpServletRequest request) {
        if (shouldSuppressLogging(ex)) {
            return;
        }
        logException("Exception caught when processing request to '" + request.getRequestURI() + "'.", ex);
    }

    private static boolean shouldSuppressLogging(TermItException ex) {
        return ex.getClass().getAnnotation(SuppressibleLogging.class) != null;
    }

    private static void logException(Throwable ex, HttpServletRequest request) {
        logException("Exception caught when processing request to '" + request.getRequestURI() + "'.", ex);
    }

    private static void logException(String message, Throwable ex) {
        // prevents from logging exceptions caused be broken connection with a client
        if (!isCausedBy(ex, AsyncRequestNotUsableException.class)) {
            LOG.error(message, ex);
        }
    }

    private static ErrorInfo errorInfo(HttpServletRequest request, Throwable e) {
        return ErrorInfo.createWithMessage(e.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ErrorInfo> persistenceException(HttpServletRequest request, PersistenceException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e.getCause()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OWLPersistenceException.class)
    public ResponseEntity<ErrorInfo> jopaException(HttpServletRequest request, OWLPersistenceException e) {
        logException("Persistence exception caught.", e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ResourceExistsException.class)
    public ResponseEntity<ErrorInfo> resourceExistsException(HttpServletRequest request, ResourceExistsException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorInfo> resourceNotFound(HttpServletRequest request, NotFoundException e) {
        // Not necessary to log NotFoundException, they may be quite frequent and do not represent an issue with the application
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorInfo> usernameNotFound(HttpServletRequest request, UsernameNotFoundException e) {
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorInfo> entityNotFoundException(HttpServletRequest request, EntityNotFoundException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorInfo> authorizationException(HttpServletRequest request, AuthorizationException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorInfo> authenticationException(HttpServletRequest request, AuthenticationException e) {
        LOG.warn("Authentication failure during HTTP request to {}: {}", request.getRequestURI(), e.getMessage());
        LOG.atDebug().setCause(e).log(e.getMessage());
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorInfo> validationException(HttpServletRequest request, ValidationException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(WebServiceIntegrationException.class)
    public ResponseEntity<ErrorInfo> webServiceIntegrationException(HttpServletRequest request,
                                                                    WebServiceIntegrationException e) {
        logException(e.getCause(), request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AnnotationGenerationException.class)
    public ResponseEntity<ErrorInfo> annotationGenerationException(HttpServletRequest request,
                                                                   AnnotationGenerationException e) {
        logException(e.getCause(), request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(TermItException.class)
    public ResponseEntity<ErrorInfo> termItException(HttpServletRequest request,
                                                     TermItException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(JsonLdException.class)
    public ResponseEntity<ErrorInfo> jsonLdException(HttpServletRequest request, JsonLdException e) {
        logException(e, request);
        return new ResponseEntity<>(
                ErrorInfo.createWithMessage("Error when processing JSON-LD.", request.getRequestURI()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorInfo> unsupportedAssetOperationException(HttpServletRequest request,
                                                                        UnsupportedOperationException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(VocabularyImportException.class)
    public ResponseEntity<ErrorInfo> vocabularyImportException(HttpServletRequest request,
                                                               VocabularyImportException e) {
        logException(e, request);
        return new ResponseEntity<>(
                ErrorInfo.createWithMessageAndMessageId(e.getMessage(), e.getMessageId(), request.getRequestURI()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> unsupportedImportMediaTypeException(HttpServletRequest request,
                                                                         UnsupportedImportMediaTypeException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> assetRemovalException(HttpServletRequest request, AssetRemovalException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> invalidParameter(HttpServletRequest request, InvalidParameterException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> maxUploadSizeExceededException(HttpServletRequest request,
                                                                    MaxUploadSizeExceededException e) {
        logException(e, request);
        return new ResponseEntity<>(ErrorInfo.createWithMessageAndMessageId(
                e.getMessage(),
                "error.file.maxUploadSizeExceeded", request.getRequestURI()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> snapshotNotEditableException(HttpServletRequest request,
                                                                  SnapshotNotEditableException e) {
        logException(e, request);
        return new ResponseEntity<>(ErrorInfo.createWithMessage(e.getMessage(), request.getRequestURI()),
                                    HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> unsupportedSearchFacetException(HttpServletRequest request,
                                                                     UnsupportedSearchFacetException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> invalidLanguageConstantException(HttpServletRequest request,
                                                                      InvalidLanguageConstantException e) {
        logException(e, request);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> invalidTermStateException(HttpServletRequest request,
                                                               InvalidTermStateException e) {
        logException(e, request);
        return new ResponseEntity<>(
                ErrorInfo.createWithMessageAndMessageId(e.getMessage(), e.getMessageId(), request.getRequestURI()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> invalidPasswordChangeRequestException(HttpServletRequest request, InvalidPasswordChangeRequestException e) {
        logException(e, request);
        return new ResponseEntity<>(ErrorInfo.createWithMessageAndMessageId(e.getMessage(), e.getMessageId(), request.getRequestURI()), HttpStatus.CONFLICT);
    }
}
