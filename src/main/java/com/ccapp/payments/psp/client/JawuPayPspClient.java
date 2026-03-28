package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawuPayConfirmRequest;
import com.ccapp.payments.psp.dto.JawuPayConfirmResponse;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutRequest;
import com.ccapp.payments.psp.dto.JawuPayPaymentMethod;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutResponse;
import com.ccapp.payments.psp.dto.JawuPayQuoteRequest;
import com.ccapp.payments.psp.dto.JawuPayQuoteResponse;

import java.util.List;

public interface JawuPayPspClient {

    /** POST /api/v1/payment/quote — obtain a payment quote before confirming */
    JawuPayQuoteResponse createQuote(JawuPayQuoteRequest request);

    /** POST /api/v1/payment/confirm — confirm the quoted payment (Idempotency-Key header) */
    JawuPayConfirmResponse confirmPayment(JawuPayConfirmRequest request, String idempotencyKey);

    /** GET /api/v1/payment/{paymentId} — fetch current payment status */
    JawuPayPaymentResponse getPayment(String paymentId);

    /** POST /api/v1/payout — initiate an outbound payout */
    JawuPayPayoutResponse createPayout(JawuPayCreatePayoutRequest request);

    /** GET /api/v1/payout/{id} — fetch current payout status */
    JawuPayPayoutDetailsResponse getPayout(String payoutId);

    /** GET /api/v1/payment-methods?country={country} — list available payment methods */
    List<JawuPayPaymentMethod> getPaymentMethods(String country);
}
