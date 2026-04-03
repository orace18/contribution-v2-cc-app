package com.ccapp.payments.psp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JawuPayApiResponse<T>(
        Integer status,
        String code,
        String message,
        T data
) {}
