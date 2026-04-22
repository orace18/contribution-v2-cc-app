package com.ccapp.mobile.common;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.SensitiveDataRedactor;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.payments.psp.client.JawuPayPspClientImpl;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.ccapp.mobile")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MobileApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiEnvelope.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiEnvelope.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiEnvelope.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiEnvelope.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : null;
        String code = resolveConstraintCode(detail);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiEnvelope.error(sanitize(code)));
    }

    private String resolveConstraintCode(String detail) {
        if (detail == null) return "DATABASE_CONSTRAINT_VIOLATION";
        if (detail.contains("uk_users_email")) return "EMAIL_ALREADY_BOUND_TO_EXISTING_ACCOUNT";
        if (detail.contains("uk_users_firebase_uid")) return "IDENTITY_CONFLICT_MANUAL_REVIEW_REQUIRED";
        if (detail.contains("uk_app_users_firebase_uid")) return "IDENTITY_CONFLICT_MANUAL_REVIEW_REQUIRED";
        if (detail.contains("uk_user_device_identifier")) return "DEVICE_ALREADY_REGISTERED";
        return "DATABASE_CONSTRAINT_VIOLATION";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiEnvelope.error(sanitize(message)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiEnvelope.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error("Invalid request payload"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiEnvelope.error("Access denied"));
    }

    @ExceptionHandler(JawuPayPspClientImpl.PSPCommunicationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handlePspCommunication(JawuPayPspClientImpl.PSPCommunicationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiEnvelope.error("Payment service unavailable, please try again"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiEnvelope.error("Internal server error"));
    }

    private String sanitize(String value) {
        return SensitiveDataRedactor.redactMessage(value);
    }
}
