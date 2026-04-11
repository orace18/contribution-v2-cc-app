package com.ccapp.mobile.common;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobileApiExceptionHandlerTest {

    private final MobileApiExceptionHandler handler = new MobileApiExceptionHandler();

    @Test
    void handleBusiness_redactsSensitiveValues() {
        ResponseEntity<ApiEnvelope<Void>> response = handler.handleBusiness(
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
