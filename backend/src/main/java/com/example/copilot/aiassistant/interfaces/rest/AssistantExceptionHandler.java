package com.example.copilot.aiassistant.interfaces.rest;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.copilot.aiassistant")
public class AssistantExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AssistantExceptionHandler.class);

    @ExceptionHandler(AssistantException.class)
    ResponseEntity<ApiError> handleAssistant(AssistantException e) {
        log.warn("requestId={} code={}", RequestIdFilter.current(), e.code());
        return respond(e.code(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<Object> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> (Object) Map.of("field", fe.getField()))
                .distinct()
                .toList();
        log.warn("requestId={} code={}", RequestIdFilter.current(), ErrorCode.AI_INPUT_INVALID);
        return respond(ErrorCode.AI_INPUT_INVALID, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalid(List.of(Map.of("field", e.getName())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException e) {
        return invalid(List.of(Map.of("field", e.getParameterName())));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException e) {
        // A validated body reports its bad fields; a bare parameter (path variable, query) reports its own name.
        List<Object> details = e.getParameterValidationResults().stream()
                .flatMap(r -> r instanceof ParameterErrors pe && !pe.getFieldErrors().isEmpty()
                        ? pe.getFieldErrors().stream().map(fe -> fe.getField())
                        : Stream.of(r.getMethodParameter().getParameterName()))
                .filter(n -> n != null)
                .distinct()
                .map(n -> (Object) Map.of("field", n))
                .toList();
        return invalid(details);
    }

    private ResponseEntity<ApiError> invalid(List<Object> details) {
        log.warn("requestId={} code={}", RequestIdFilter.current(), ErrorCode.AI_INPUT_INVALID);
        return respond(ErrorCode.AI_INPUT_INVALID, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("requestId={} code={}", RequestIdFilter.current(), ErrorCode.AI_INPUT_INVALID);
        return respond(ErrorCode.AI_INPUT_INVALID, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        log.warn("requestId={} code={}", RequestIdFilter.current(), ErrorCode.AI_FORBIDDEN);
        return respond(ErrorCode.AI_FORBIDDEN, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        // Only the exception class is logged; the message may contain sensitive text and is never returned.
        log.error("requestId={} unexpected exception type={}", RequestIdFilter.current(), e.getClass().getName());
        return respond(ErrorCode.AI_INTERNAL_ERROR, List.of());
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, List<Object> details) {
        return ResponseEntity.status(code.status()).body(ApiError.of(code, details));
    }
}
