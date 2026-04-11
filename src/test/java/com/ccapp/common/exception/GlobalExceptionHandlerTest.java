package com.ccapp.common.exception;

import com.ccapp.common.api.CommonResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMessageNotReadable_shouldReturnBadRequest() {
        ResponseEntity<CommonResponse<Void>> response = handler.handleMessageNotReadable(
                new HttpMessageNotReadableException("Unsupported enum value")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid request payload", response.getBody().message());
        assertEquals(false, response.getBody().success());
    }

    @Test
    void handleBusiness_redactsSensitiveValues() {
        ResponseEntity<CommonResponse<Void>> response = handler.handleBusiness(
                new BusinessException("Authorization=Bearer raw-token pin=1234 refreshToken=refresh-secret")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Authorization=Bearer [REDACTED] pin=[REDACTED] refreshToken=[REDACTED]", response.getBody().message());
        assertFalse(response.getBody().message().contains("raw-token"));
        assertFalse(response.getBody().message().contains("1234"));
        assertFalse(response.getBody().message().contains("refresh-secret"));
    }
}
