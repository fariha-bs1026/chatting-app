package com.fariha.chattingapp.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final MessageSource messageSource;

    public ApiExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception, Locale locale) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status).body(error(status, exception.getReason(), locale));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception, Locale locale) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "Validation failed" : fieldError.getDefaultMessage();
        Map<String, Object> body = error(HttpStatus.BAD_REQUEST, message, locale);
        Map<String, String> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        if (!fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException exception,
            Locale locale
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, message, locale));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            Locale locale
    ) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "Malformed JSON request", locale));
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> handleMissingRequestPart(Exception exception, Locale locale) {
        return ResponseEntity.badRequest().body(error(
                HttpStatus.BAD_REQUEST,
                messageSource.getMessage("media.file.required", null, locale),
                locale
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            Locale locale
    ) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                messageSource.getMessage("media.file.too-large", null, locale),
                locale
        ));
    }

    private Map<String, Object> error(HttpStatus status, String message, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", localize(message == null ? status.getReasonPhrase() : message, locale));
        return body;
    }

    private String localize(String message, Locale locale) {
        if (message.startsWith("{") && message.endsWith("}")) {
            return messageSource.getMessage(message.substring(1, message.length() - 1), null, message, locale);
        }
        return message;
    }
}
