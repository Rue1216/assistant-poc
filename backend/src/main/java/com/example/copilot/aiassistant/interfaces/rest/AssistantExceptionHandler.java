package com.example.copilot.aiassistant.interfaces.rest;

import com.example.copilot.aiassistant.application.support.RequestContext;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.exception.ErrorDetail;
import com.example.copilot.aiassistant.domain.exception.FieldDetail;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
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
@Slf4j
public class AssistantExceptionHandler {

    @ExceptionHandler(AssistantException.class)
    ResponseEntity<ApiError> handleAssistant(AssistantException e) {
        log.warn("traceId={} code={}", RequestContext.currentTraceId(), e.code());
        return respond(e.code(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<FieldDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldDetail(fe.getField()))
                .distinct()
                .toList();
        log.warn("traceId={} code={}", RequestContext.currentTraceId(), ErrorCode.AI_INPUT_INVALID);
        return respond(ErrorCode.AI_INPUT_INVALID, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalid(List.of(new FieldDetail(e.getName())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException e) {
        return invalid(List.of(new FieldDetail(e.getParameterName())));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException e) {
        // A validated body reports its bad fields; a bare parameter (path variable, query) reports its own name.
        List<FieldDetail> details = e.getParameterValidationResults().stream()
                .flatMap(r -> r instanceof ParameterErrors pe && !pe.getFieldErrors().isEmpty()
                        ? pe.getFieldErrors().stream().map(fe -> fe.getField())
                        : Stream.of(r.getMethodParameter().getParameterName()))
                .filter(n -> n != null)
                .distinct()
                .map(n -> new FieldDetail(n))
                .toList();
        return invalid(details);
    }

    private ResponseEntity<ApiError> invalid(List<FieldDetail> details) {
        log.warn("traceId={} code={}", RequestContext.currentTraceId(), ErrorCode.AI_INPUT_INVALID);
        return respond(ErrorCode.AI_INPUT_INVALID, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("traceId={} code={}", RequestContext.currentTraceId(), ErrorCode.AI_INPUT_INVALID);
        return respond(ErrorCode.AI_INPUT_INVALID, unreadableFieldDetails(e));
    }

    /** The field a Jackson type mismatch points at (e.g. an unparsable date); empty for any other unreadable body. */
    private static List<FieldDetail> unreadableFieldDetails(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof MismatchedInputException mie) {
                return mie.getPath().stream()
                        .map(JacksonException.Reference::getPropertyName)
                        .filter(n -> n != null)
                        .reduce((first, last) -> last)
                        .map(n -> List.of(new FieldDetail(n)))
                        .orElse(List.of());
            }
        }
        return List.of();
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        log.warn("traceId={} code={}", RequestContext.currentTraceId(), ErrorCode.AI_FORBIDDEN);
        return respond(ErrorCode.AI_FORBIDDEN, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        // Only the exception class is logged; the message may contain sensitive text and is never returned.
        log.error("traceId={} unexpected exception type={}", RequestContext.currentTraceId(), e.getClass().getName());
        return respond(ErrorCode.AI_INTERNAL_ERROR, List.of());
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, List<? extends ErrorDetail> details) {
        return ResponseEntity.status(code.status()).body(ApiError.of(code, details));
    }
}
