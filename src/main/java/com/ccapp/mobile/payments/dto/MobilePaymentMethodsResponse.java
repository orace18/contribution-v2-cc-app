package com.ccapp.mobile.payments.dto;

import java.util.List;

public record MobilePaymentMethodsResponse(
        List<MobilePaymentMethodResponse> methods
) {
}
