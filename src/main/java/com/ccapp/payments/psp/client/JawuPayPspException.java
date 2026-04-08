package com.ccapp.payments.psp.client;

import com.ccapp.common.exception.BusinessException;

import java.util.Locale;

public class JawuPayPspException extends BusinessException {

    private final Integer httpStatusCode;
    private final String responseBody;

    public JawuPayPspException(String message) {
        super(message);
        this.httpStatusCode = null;
        this.responseBody = null;
    }

    public JawuPayPspException(String message, Integer httpStatusCode, String responseBody) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.responseBody = responseBody;
    }

    public Integer httpStatusCode() {
        return httpStatusCode;
    }

    public String responseBody() {
        return responseBody;
    }

    public boolean isNotFoundLike() {
        if (httpStatusCode != null && httpStatusCode == 404) {
            return true;
        }
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        return responseBody.toLowerCase(Locale.ROOT).contains("not found");
    }
}
