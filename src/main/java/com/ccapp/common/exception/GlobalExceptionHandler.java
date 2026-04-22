package com.ccapp.common.exception;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SensitiveDataRedactor;
import com.ccapp.prayer.exception.PrayerPhase1NotImplementedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<CommonResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CommonResponse.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<CommonResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CommonResponse.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<CommonResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponse.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(PrayerPhase1NotImplementedException.class)
    public ResponseEntity<CommonResponse<Void>> handlePrayerPhase1NotImplemented(PrayerPhase1NotImplementedException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(CommonResponse.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.error(sanitize(message)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.error(sanitize(ex.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error("Invalid request payload"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Access denied"
                : sanitize(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommonResponse.error(message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.error("Resource not found"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CommonResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        String code = resolveConstraintCode(detail);
        log.error("[identity] DataIntegrityViolationException constraint-code={} detail={}", code, detail);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponse.error(code));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleUnexpected(Exception ex) {
        log.error("[system] Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.error("Internal server error"));
    }

    private String resolveConstraintCode(String detail) {
        if (detail == null) return "DATABASE_CONSTRAINT_VIOLATION";
        if (detail.contains("uk_users_email")) return "EMAIL_ALREADY_BOUND_TO_EXISTING_ACCOUNT";
        if (detail.contains("uk_users_firebase_uid")) return "IDENTITY_CONFLICT_MANUAL_REVIEW_REQUIRED";
        if (detail.contains("uk_app_users_firebase_uid")) return "IDENTITY_CONFLICT_MANUAL_REVIEW_REQUIRED";
        if (detail.contains("uk_user_device_identifier")) return "DEVICE_ALREADY_REGISTERED";
        return "DATABASE_CONSTRAINT_VIOLATION";
    }

    private String sanitize(String value) {
        return SensitiveDataRedactor.redactMessage(value);
    }
}
