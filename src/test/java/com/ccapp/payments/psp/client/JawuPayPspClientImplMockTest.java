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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the mock mode of {@link JawuPayPspClientImpl} — no HTTP calls are made.
 */
class JawuPayPspClientImplMockTest {

    private JawuPayPspClientImpl client;

    @BeforeEach
    void setUp() {
        client = new JawuPayPspClientImpl();
        ReflectionTestUtils.setField(client, "baseUrl",
                "https://api-gateway-dev.jawupay.com/orchestrator-service");
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "mockEnabled", true);
        client.init();
    }

    @Test
    void createQuote_mockMode_returnsZeroFeesAndSameAmount() {
        JawuPayQuoteRequest request = new JawuPayQuoteRequest(150000L, "CI", "MOBILE_MONEY_MTN", "client-1", null);

        JawuPayQuoteResponse response = client.createQuote(request);

        assertThat(response.quoteId()).isNotNull();
        assertThat(response.amount()).isEqualTo(150000L);
        assertThat(response.fees()).isEqualTo(0L);
        assertThat(response.totalAmount()).isEqualTo(150000L);
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void confirmPayment_mockMode_returnsPendingWithRedirectUrl() {
        JawuPayConfirmRequest request = new JawuPayConfirmRequest(
                java.util.UUID.randomUUID(),
                "XOF",
                JawuPayConfirmRequest.PayerInfo.forMobileMoney("22501234567", "MTN")
        );

        JawuPayConfirmResponse response = client.confirmPayment(request, "idem-key-1");

        assertThat(response.paymentId()).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.redirectUrl()).contains("checkout.jawupay.example");
        assertThat(response.pspProvider()).isEqualTo("MOCK_PSP");
    }

    @Test
    void getPayment_mockMode_returnsPendingForGivenId() {
        String paymentId = java.util.UUID.randomUUID().toString();

        JawuPayPaymentResponse response = client.getPayment(paymentId);

        assertThat(response.paymentId()).isEqualTo(java.util.UUID.fromString(paymentId));
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.currency()).isEqualTo("XOF");
    }

    @Test
    void createPayout_mockMode_returnsPendingResponse() {
        JawuPayCreatePayoutRequest request = new JawuPayCreatePayoutRequest(
                50000L, "XOF", "CI", "MOBILE_MONEY_MTN", "client-1", "+22501234567", "MTN");

        JawuPayPayoutResponse response = client.createPayout(request);

        assertThat(response.payoutId()).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.pspProvider()).isEqualTo("MOCK_PSP");
    }

    @Test
    void getPayout_mockMode_returnsPendingForGivenId() {
        String payoutId = java.util.UUID.randomUUID().toString();

        JawuPayPayoutDetailsResponse response = client.getPayout(payoutId);

        assertThat(response.payoutId()).isEqualTo(java.util.UUID.fromString(payoutId));
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.currency()).isEqualTo("XOF");
    }

    @Test
    void getPaymentMethods_mockMode_returnsMtnAndOrange() {
        List<JawuPayPaymentMethod> methods = client.getPaymentMethods("CI");

        assertThat(methods).hasSize(2);
        assertThat(methods).extracting(JawuPayPaymentMethod::id)
                .containsExactlyInAnyOrder("MOBILE_MONEY_MTN", "MOBILE_MONEY_ORANGE");
    }
}
