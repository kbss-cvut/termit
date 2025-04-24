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
package cz.cvut.kbss.termit.websocket.handler;

import cz.cvut.kbss.jopa.exceptions.EntityNotFoundException;
import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jsonld.exception.JsonLdException;
import cz.cvut.kbss.termit.exception.AnnotationGenerationException;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.InvalidIdentifierException;
import cz.cvut.kbss.termit.exception.InvalidLanguageConstantException;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeRequestException;
import cz.cvut.kbss.termit.exception.InvalidTermStateException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.SnapshotNotEditableException;
import cz.cvut.kbss.termit.exception.SuppressibleLogging;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.UnsupportedSearchFacetException;
import cz.cvut.kbss.termit.exception.UnsupportedTextAnalysisLanguageException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.util.ExceptionUtils.findCause;

/**
 * @implSpec Should reflect {@link cz.cvut.kbss.termit.rest.handler.RestExceptionHandler}.<br>
 * In order for the delegation to work, the method signature of MessageExceptionHandler methods must be {@code (Message<?>, Exception)}
 */
@SendToUser
@ControllerAdvice
public class WebSocketExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

    private static String destination(Message<?> message) {
        return message.getHeaders().getOrDefault("destination", "(missing destination)").toString();
    }

    private static boolean hasDestination(Message<?> message) {
        final String dst = (String) message.getHeaders().getOrDefault("destination", "");
        return dst != null && !dst.isBlank();
    }

    private static void logException(TermItException ex, Message<?> message) {
        if (shouldSuppressLogging(ex)) {
            return;
        }
        logException("Exception caught when processing request to '" + destination(message) + "'.", ex);
    }

    private static boolean shouldSuppressLogging(TermItException ex) {
        return ex.getClass().getAnnotation(SuppressibleLogging.class) != null;
    }

    private static void logException(Throwable ex, Message<?> message) {
        logException("Exception caught when processing request to '" + destination(message) + "'.", ex);
    }

    private static void logException(String message, Throwable ex) {
        // Prevents exceptions caused by broken connection with a client from logging
        if (findCause(ex, AsyncRequestNotUsableException.class).isEmpty()) {
            LOG.error(message, ex);
        }
    }

    private static ErrorInfo errorInfo(Message<?> message, Throwable e) {
        return ErrorInfo.createWithMessage(e.getMessage(), destination(message));
    }

    private static ErrorInfo errorInfo(Message<?> message, TermItException e) {
        return ErrorInfo.createParametrizedWithMessage(e.getMessage(), e.getMessageId(), destination(message),
                                                       e.getParameters());
    }

    /**
     * Searches available methods annotated with {@link MessageExceptionHandler} in this class
     * when the method signature matches {@code (Message<?>, Exception)}
     * and the exception parameter is assignable from the supplied throwable
     * the method is called.
     *
     * @param message the associated message
     * @param throwable the exception to handle
     * @return true when a method was found and called, false otherwise
     */
    public boolean delegate(Message<?> message, Throwable throwable) {
        try {
            return delegateInternal(message, throwable);
        } catch (InvocationTargetException invEx) {
            // Exception handler method threw an exception
            LOG.error("Exception thrown during exception handler invocation", invEx);
        } catch (IllegalAccessException unexpected) {
            // is checked by delegateInternal
        }
        return false;
    }

    /**
     * Searches available methods annotated with {@link MessageExceptionHandler} in this class
     * when the method signature matches {@code (Message<?>, Exception)}
     * and the exception parameter is assignable from the supplied throwable
     * the method is called.
     *
     * @param message the associated message
     * @param throwable the exception to handle
     * @return true when a method was found and called, false otherwise
     * @throws IllegalArgumentException never
     * @throws IllegalAccessException never
     * @throws InvocationTargetException when the exception handler method throws an exception
     */
    private boolean delegateInternal(Message<?> message, Throwable throwable)
            throws InvocationTargetException, IllegalAccessException {
        // handle only exceptions
        if (throwable instanceof Exception exception) {
            // find all methods annotated with MessageExceptionHandler
            List<Method> methods = Arrays.stream(this.getClass().getMethods())
                                         .filter(m -> m.isAnnotationPresent(MessageExceptionHandler.class)).toList();
            for (final Method method : methods) {
                // check for reflection access to prevent IllegalAccessException
                if (!method.canAccess(this)) {
                    continue;
                }
                // we are interested only in methods with exactly two parameters (message, exception)
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2) {
                    continue;
                }
                // check if the MessageExceptionHandler annotation has value with allowed exceptions
                Class<? extends Throwable>[] allowedExceptions = Optional.ofNullable(method.getAnnotation(MessageExceptionHandler.class))
                                                                         .map(MessageExceptionHandler::value).orElseGet(() -> new Class[0]);
                // if the exception is not allowed by the annotation, skip the method
                if (allowedExceptions.length > 0 && Arrays.stream(allowedExceptions).noneMatch(e -> e.isAssignableFrom(exception.getClass()))) {
                    continue;
                }
                // validate the method signature
                if (params[0].isAssignableFrom(message.getClass()) && params[1].isAssignableFrom(exception.getClass())) {
                    // call the method with message, exception parameters
                    method.invoke(this, message, exception);
                    return true; // exception was handled
                }
            }
        }
        // throwable is not an exception or no suitable method was found
        return false;
    }

    @MessageExceptionHandler
    public void messageDeliveryException(Message<?> message, MessageDeliveryException e) {
        if (!(e.getCause() instanceof MessageDeliveryException) && delegate(message, e.getCause())) {
            return;
        }

        // messages without destination will be logged only on trace
        (hasDestination(message) ? LOG.atError() : LOG.atTrace())
                .setMessage("Failed to send message with destination {}: {}")
                .addArgument(() -> destination(message))
                .addArgument(e.getMessage())
                .setCause(e.getCause())
                .log();
    }

    @MessageExceptionHandler(PersistenceException.class)
    public ErrorInfo persistenceException(Message<?> message, PersistenceException e) {
        logException(e, message);
        return errorInfo(message, e.getCause());
    }

    @MessageExceptionHandler(OWLPersistenceException.class)
    public ErrorInfo jopaException(Message<?> message, OWLPersistenceException e) {
        logException("Persistence exception caught.", e);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(ResourceExistsException.class)
    public ErrorInfo resourceExistsException(Message<?> message, ResourceExistsException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(NotFoundException.class)
    public ErrorInfo resourceNotFound(Message<?> message, NotFoundException e) {
        // Not necessary to log NotFoundException, they may be quite frequent and do not represent an issue with the application
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(UsernameNotFoundException.class)
    public ErrorInfo usernameNotFound(Message<?> message, UsernameNotFoundException e) {
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(EntityNotFoundException.class)
    public ErrorInfo entityNotFoundException(Message<?> message, EntityNotFoundException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(AuthorizationException.class)
    public ErrorInfo authorizationException(Message<?> message, AuthorizationException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler({AuthenticationException.class, AuthenticationServiceException.class})
    public ErrorInfo authenticationException(Message<?> message, AuthenticationException e) {
        LOG.atWarn().setMessage("Authentication failure during message processing: {}\nMessage: {}")
           .addArgument(e.getMessage()).addArgument(message::toString).log();

        if (ExceptionUtils.findCause(e, JwtException.class).isPresent()) {
            return errorInfo(message, e);
        }

        LOG.atDebug().setCause(e).log(e.getMessage());
        return errorInfo(message, e);
    }

    /**
     * Fired, for example, on method security violation
     */
    @MessageExceptionHandler(AccessDeniedException.class)
    public ErrorInfo accessDeniedException(Message<?> message, AccessDeniedException e) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getUser() != null) {
            LOG.atWarn().setMessage("[{}] Unauthorized access: {}").addArgument(() -> accessor.getUser().getName())
               .addArgument(e.getMessage()).log();
        }
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(ValidationException.class)
    public ErrorInfo validationException(Message<?> message, ValidationException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(WebServiceIntegrationException.class)
    public ErrorInfo webServiceIntegrationException(Message<?> message, WebServiceIntegrationException e) {
        logException(e.getCause(), message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(AnnotationGenerationException.class)
    public ErrorInfo annotationGenerationException(Message<?> message, AnnotationGenerationException e) {
        logException(e.getCause(), message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(TermItException.class)
    public ErrorInfo termItException(Message<?> message, TermItException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(JsonLdException.class)
    public ErrorInfo jsonLdException(Message<?> message, JsonLdException e) {
        logException(e, message);
        return ErrorInfo.createWithMessage("Error when processing JSON-LD.", destination(message));
    }

    @MessageExceptionHandler(UnsupportedOperationException.class)
    public ErrorInfo unsupportedAssetOperationException(Message<?> message, UnsupportedOperationException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler(VocabularyImportException.class)
    public ErrorInfo vocabularyImportException(Message<?> message, VocabularyImportException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo unsupportedImportMediaTypeException(Message<?> message, UnsupportedImportMediaTypeException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo assetRemovalException(Message<?> message, AssetRemovalException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo invalidParameter(Message<?> message, InvalidParameterException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo maxUploadSizeExceededException(Message<?> message, MaxUploadSizeExceededException e) {
        logException(e, message);
        return ErrorInfo.createWithMessageAndMessageId(e.getMessage(), "error.file.maxUploadSizeExceeded",
                                                       destination(message));
    }

    @MessageExceptionHandler
    public ErrorInfo snapshotNotEditableException(Message<?> message, SnapshotNotEditableException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo unsupportedSearchFacetException(Message<?> message, UnsupportedSearchFacetException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo invalidLanguageConstantException(Message<?> message, InvalidLanguageConstantException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo invalidTermStateException(Message<?> message, InvalidTermStateException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo invalidPasswordChangeRequestException(Message<?> message,
                                                           InvalidPasswordChangeRequestException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo invalidIdentifierException(Message<?> message, InvalidIdentifierException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo uriSyntaxException(Message<?> message, URISyntaxException e) {
        logException(e, message);
        return errorInfo(message, e);
    }

    @MessageExceptionHandler
    public ErrorInfo unsupportedTextAnalysisLanguageException(Message<?> message,
                                                              UnsupportedTextAnalysisLanguageException e) {
        logException(e, message);
        return errorInfo(message, e);
    }
}
