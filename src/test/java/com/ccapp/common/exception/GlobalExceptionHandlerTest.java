package com.ccapp.common.exception;

import com.ccapp.common.api.CommonResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
