/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest.handler;

import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jsonld.exception.JsonLdException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;

/**
 * Exception handlers for REST controllers.
 * <p>
 * The general pattern should be that unless an exception can be handled in a more appropriate place it bubbles up to a
 * REST controller which originally received the request. There, it is caught by this handler, logged and a reasonable
 * error message is returned to the user.
 */
@ControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

    private static void logException(TermItException ex) {
        if (shouldSuppressLogging(ex)) {
            return;
        }
        logException("Exception caught.", ex);
    }

    private static boolean shouldSuppressLogging(TermItException ex) {
        return ex.getClass().getAnnotation(SuppressibleLogging.class) != null;
    }

    private static void logException(Throwable ex) {
        logException("Exception caught.", ex);
    }

    private static void logException(String message, Throwable ex) {
        LOG.error(message, ex);
    }

    private static ErrorInfo errorInfo(HttpServletRequest request, Throwable e) {
        return ErrorInfo.createWithMessage(e.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ErrorInfo> persistenceException(HttpServletRequest request, PersistenceException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e.getCause()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OWLPersistenceException.class)
    public ResponseEntity<ErrorInfo> jopaException(HttpServletRequest request, OWLPersistenceException e) {
        logException("Persistence exception caught.", e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ResourceExistsException.class)
    public ResponseEntity<ErrorInfo> resourceExistsException(HttpServletRequest request, ResourceExistsException e) {
        logException(e);
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


    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorInfo> authorizationException(HttpServletRequest request, AuthorizationException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorInfo> validationException(HttpServletRequest request, ValidationException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(WebServiceIntegrationException.class)
    public ResponseEntity<ErrorInfo> webServiceIntegrationException(HttpServletRequest request,
                                                                    WebServiceIntegrationException e) {
        logException(e.getCause());
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AnnotationGenerationException.class)
    public ResponseEntity<ErrorInfo> annotationGenerationException(HttpServletRequest request,
                                                                   AnnotationGenerationException e) {
        logException(e.getCause());
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(TermItException.class)
    public ResponseEntity<ErrorInfo> termItException(HttpServletRequest request,
                                                     TermItException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(JsonLdException.class)
    public ResponseEntity<ErrorInfo> jsonLdException(HttpServletRequest request, JsonLdException e) {
        logException(e);
        return new ResponseEntity<>(
                ErrorInfo.createWithMessage("Error when processing JSON-LD.", request.getRequestURI()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorInfo> unsupportedAssetOperationException(HttpServletRequest request,
                                                                        UnsupportedOperationException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DisabledOperationException.class)
    public ResponseEntity<ErrorInfo> disabledOperationException(HttpServletRequest request, DisabledOperationException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(VocabularyImportException.class)
    public ResponseEntity<ErrorInfo> vocabularyImportException(HttpServletRequest request,
                                                               VocabularyImportException e) {
        logException(e);
        return new ResponseEntity<>(
                ErrorInfo.createWithMessageAndMessageId(e.getMessage(), e.getMessageId(), request.getRequestURI()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> unsupportedImportMediaTypeException(HttpServletRequest request,
                                                                         UnsupportedImportMediaTypeException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> vocabularyRemovalException(HttpServletRequest request,
                                                                VocabularyRemovalException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> termRemovalException(HttpServletRequest request,
                                                          TermRemovalException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorInfo> invalidParameter(HttpServletRequest request,
                                                      InvalidParameterException e) {
        logException(e);
        return new ResponseEntity<>(errorInfo(request, e), HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
