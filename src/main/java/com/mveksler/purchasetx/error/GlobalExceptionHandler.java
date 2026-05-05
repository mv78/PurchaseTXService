// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.error;

import com.mveksler.purchasetx.error.exceptions.NoExchangeRateAvailableException;
import com.mveksler.purchasetx.error.exceptions.PurchaseNotFoundException;
import com.mveksler.purchasetx.error.exceptions.TreasuryApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("Validation failed: {}", fieldErrors);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        problem.setTitle("Validation error");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(PurchaseNotFoundException.class)
    public ProblemDetail handlePurchaseNotFound(PurchaseNotFoundException ex) {
        log.warn("Purchase not found: {}", ex.getPurchaseId());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Purchase not found");
        problem.setProperty("purchaseId", ex.getPurchaseId());
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing required parameter '{}' of type '{}'", ex.getParameterName(), ex.getParameterType());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing"
        );
        problem.setTitle("Missing parameter");
        problem.setProperty("parameter", ex.getParameterName());
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}': value='{}' expected={}",
                ex.getName(), ex.getValue(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'"
                        + (ex.getRequiredType() != null ? ". Expected a " + ex.getRequiredType().getSimpleName() : "")
        );
        problem.setTitle("Invalid parameter");
        problem.setProperty("parameter", ex.getName());
        problem.setProperty("rejectedValue", String.valueOf(ex.getValue()));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedRequest(HttpMessageNotReadableException ex) {
        log.warn("Malformed request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body could not be parsed"
        );
        problem.setTitle("Malformed request");

        if (ex.getCause() instanceof InvalidFormatException ife) {
            String fieldPath = extractFieldPath(ife);
            if (fieldPath != null && !fieldPath.isEmpty()) {
                problem.setProperty("field", fieldPath);
                problem.setProperty("rejectedValue", String.valueOf(ife.getValue()));

                // Provide format hint based on the expected target type
                String hint = formatHintFor(ife.getTargetType(), fieldPath);
                String detail = "Invalid value for field '" + fieldPath + "'";
                if (hint != null) {
                    detail += ". " + hint;
                    problem.setProperty("expectedFormat", hint);
                }
                problem.setDetail(detail);
            }
        }

        return problem;
    }

    private String formatHintFor(Class<?> targetType, String fieldPath) {
        if (targetType == null) return null;
        if (LocalDate.class.equals(targetType)) {
            return "Expected ISO-8601 date format (yyyy-MM-dd)";
        }
        if (BigDecimal.class.equals(targetType)) {
            return "Expected a numeric value";
        }
        if (UUID.class.equals(targetType)) {
            return "Expected a UUID";
        }
        return null;
    }

    private String extractFieldPath(InvalidFormatException ife) {
        if (ife.getPath() == null) return null;
        return ife.getPath().stream()
                .map(ref -> ref.getPropertyName())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("."));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        problem.setTitle("Internal server error");
        return problem;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '" + ex.getContentType() + "' is not supported. Use application/json."
        );
        problem.setTitle("Unsupported media type");
        problem.setProperty("supportedMediaTypes",
                ex.getSupportedMediaTypes().stream().map(Object::toString).toList());
        return problem;
    }

    @ExceptionHandler(TreasuryApiException.class)
    public ProblemDetail handleTreasuryApiException(TreasuryApiException ex) {
        log.error("Treasury API error: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Exchange rate service is currently unavailable"
        );
        problem.setTitle("Upstream service unavailable");
        return problem;
    }

    @ExceptionHandler(NoExchangeRateAvailableException.class)
    public ProblemDetail handleNoExchangeRate(NoExchangeRateAvailableException ex) {
        log.warn("No exchange rate available: purchaseId={} country={} currency={} date={}",
                ex.getPurchaseId(), ex.getCountry(), ex.getCurrency(), ex.getPurchaseDate());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        problem.setTitle("Exchange rate not available");
        problem.setProperty("purchaseId", ex.getPurchaseId());
        problem.setProperty("country", ex.getCountry());
        problem.setProperty("currency", ex.getCurrency());
        problem.setProperty("purchaseDate", ex.getPurchaseDate());
        return problem;
    }
}