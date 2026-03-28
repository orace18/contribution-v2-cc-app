package com.ccapp.payments.psp.client;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.payments.psp.dto.JawuPayConfirmRequest;
import com.ccapp.payments.psp.dto.JawuPayConfirmResponse;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutRequest;
import com.ccapp.payments.psp.dto.JawuPayPaymentMethod;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutResponse;
import com.ccapp.payments.psp.dto.JawuPayQuoteRequest;
import com.ccapp.payments.psp.dto.JawuPayQuoteResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Real HTTP adapter for the JawuPay PSP orchestrator service.
 * Base URL: {@code https://api-gateway-dev.jawupay.com/orchestrator-service}
 * <p>
 * When {@code psp.jawupay.mock-enabled=true} all methods return deterministic mock
 * responses so the service layer can be exercised in dev/test without a live PSP.
 * <p>
 * Amount convention: JawuPay uses {@code int64} minor units throughout (e.g. 150000
 * for 1 500 XOF). The adapter receives minor-unit longs from callers and returns them
 * as-is. Conversion from {@link java.math.BigDecimal} to minor units is the
 * responsibility of the service layer (see {@code JawuPayAmountConverter}).
 */
@Component
@Slf4j
public class JawuPayPspClientImpl implements JawuPayPspClient {

    @Value("${psp.jawupay.base-url:https://api-gateway-dev.jawupay.com/orchestrator-service}")
    private String baseUrl;

    @Value("${psp.jawupay.api-key:}")
    private String apiKey;

    @Value("${psp.jawupay.mock-enabled:false}")
    private boolean mockEnabled;

    private RestClient restClient;

    @PostConstruct
    void init() {
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, resp) -> {
                            throw new BusinessException(
                                    "JawuPay PSP error: HTTP " + resp.getStatusCode()
                                            + " for " + req.getMethod() + " " + req.getURI());
                        })
                .build();
    }

    // -----------------------------------------------------------------------
    // Payment quote
    // -----------------------------------------------------------------------

    @Override
    public JawuPayQuoteResponse createQuote(JawuPayQuoteRequest request) {
        log.info("PSP request provider=jawupay endpoint=POST /api/v1/payment/quote body={}", request);
        if (mockEnabled) {
            JawuPayQuoteResponse response = new JawuPayQuoteResponse(
                    UUID.randomUUID(),
                    request.amount(),
                    0L,
                    request.amount(),
                    OffsetDateTime.now().plusMinutes(5)
            );
            log.info("PSP response (mock) endpoint=POST /api/v1/payment/quote body={}", response);
            return response;
        }
        JawuPayQuoteResponse response = restClient.post()
                .uri("/api/v1/payment/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JawuPayQuoteResponse.class);
        log.info("PSP response endpoint=POST /api/v1/payment/quote body={}", response);
        return response;
    }

    // -----------------------------------------------------------------------
    // Payment confirm
    // -----------------------------------------------------------------------

    @Override
    public JawuPayConfirmResponse confirmPayment(JawuPayConfirmRequest request, String idempotencyKey) {
        log.info("PSP request provider=jawupay endpoint=POST /api/v1/payment/confirm body={}", request);
        if (mockEnabled) {
            UUID paymentId = UUID.randomUUID();
            JawuPayConfirmResponse response = new JawuPayConfirmResponse(
                    paymentId,
                    "PENDING",
                    "MOCK_PSP",
                    "https://checkout.jawupay.example/pay/" + paymentId,
                    null,
                    "MOCK-REF-" + paymentId
            );
            log.info("PSP response (mock) endpoint=POST /api/v1/payment/confirm body={}", response);
            return response;
        }
        JawuPayConfirmResponse response = restClient.post()
                .uri("/api/v1/payment/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JawuPayConfirmResponse.class);
        log.info("PSP response endpoint=POST /api/v1/payment/confirm body={}", response);
        return response;
    }

    // -----------------------------------------------------------------------
    // Get payment
    // -----------------------------------------------------------------------

    @Override
    public JawuPayPaymentResponse getPayment(String paymentId) {
        log.info("PSP request provider=jawupay endpoint=GET /api/v1/payment/{}", paymentId);
        if (mockEnabled) {
            JawuPayPaymentResponse response = new JawuPayPaymentResponse(
                    UUID.fromString(paymentId), null, "PENDING", 0L, "XOF",
                    null, null, "MOCK_PSP", null, OffsetDateTime.now(), OffsetDateTime.now());
            log.info("PSP response (mock) endpoint=GET /api/v1/payment/{} body={}", paymentId, response);
            return response;
        }
        JawuPayPaymentResponse response = restClient.get()
                .uri("/api/v1/payment/{paymentId}", paymentId)
                .retrieve()
                .body(JawuPayPaymentResponse.class);
        log.info("PSP response endpoint=GET /api/v1/payment/{} body={}", paymentId, response);
        return response;
    }

    // -----------------------------------------------------------------------
    // Payout
    // -----------------------------------------------------------------------

    @Override
    public JawuPayPayoutResponse createPayout(JawuPayCreatePayoutRequest request) {
        log.info("PSP request provider=jawupay endpoint=POST /api/v1/payout body={}", request);
        if (mockEnabled) {
            JawuPayPayoutResponse response = new JawuPayPayoutResponse(
                    UUID.randomUUID(), "PENDING", "MOCK_PSP", "MOCK-PAYOUT-REF-" + UUID.randomUUID());
            log.info("PSP response (mock) endpoint=POST /api/v1/payout body={}", response);
            return response;
        }
        JawuPayPayoutResponse response = restClient.post()
                .uri("/api/v1/payout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JawuPayPayoutResponse.class);
        log.info("PSP response endpoint=POST /api/v1/payout body={}", response);
        return response;
    }

    @Override
    public JawuPayPayoutDetailsResponse getPayout(String payoutId) {
        log.info("PSP request provider=jawupay endpoint=GET /api/v1/payout/{}", payoutId);
        if (mockEnabled) {
            JawuPayPayoutDetailsResponse response = new JawuPayPayoutDetailsResponse(
                    UUID.fromString(payoutId), null, "PENDING", 0L, "XOF",
                    null, null, "MOCK_PSP", null, OffsetDateTime.now(), OffsetDateTime.now());
            log.info("PSP response (mock) endpoint=GET /api/v1/payout/{} body={}", payoutId, response);
            return response;
        }
        JawuPayPayoutDetailsResponse response = restClient.get()
                .uri("/api/v1/payout/{id}", payoutId)
                .retrieve()
                .body(JawuPayPayoutDetailsResponse.class);
        log.info("PSP response endpoint=GET /api/v1/payout/{} body={}", payoutId, response);
        return response;
    }

    // -----------------------------------------------------------------------
    // Payment methods
    // -----------------------------------------------------------------------

    @Override
    public List<JawuPayPaymentMethod> getPaymentMethods(String country) {
        log.info("PSP request provider=jawupay endpoint=GET /api/v1/payment-methods country={}", country);
        if (mockEnabled) {
            List<JawuPayPaymentMethod> response = List.of(
                    new JawuPayPaymentMethod("MOBILE_MONEY_MTN", "MTN Mobile Money"),
                    new JawuPayPaymentMethod("MOBILE_MONEY_ORANGE", "Orange Money")
            );
            log.info("PSP response (mock) endpoint=GET /api/v1/payment-methods body={}", response);
            return response;
        }
        List<JawuPayPaymentMethod> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/payment-methods")
                        .queryParam("country", country)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        log.info("PSP response endpoint=GET /api/v1/payment-methods body={}", response);
        return response;
    }
}
