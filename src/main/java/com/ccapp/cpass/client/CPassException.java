package com.ccapp.cpass.client;

import com.ccapp.common.exception.BusinessException;

public class CPassException extends BusinessException {
    public CPassException(String code) {
        super(code);
    }
}
