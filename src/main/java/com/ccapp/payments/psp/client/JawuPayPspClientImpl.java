package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawuPayApiResponse;
import com.ccapp.payments.psp.dto.JawuPayConfirmRequest;
import com.ccapp.payments.psp.dto.JawuPayConfirmResponse;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutRequest;
import com.ccapp.payments.psp.dto.JawuPayPaymentMethod;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutResponse;
import com.ccapp.payments.psp.dto.JawuPayQuoteRequest;
import com.ccapp.payments.psp.dto.JawuPayQuoteResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

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

    @Value("${psp.jawupay.hmac-secret:}")
    private String hmacSecret;

    @Value("${psp.jawupay.mock-enabled:false}")
    private boolean mockEnabled;

    private byte[] hmacSecretBytes;

    @Value("${psp.jawupay.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs = 5000;

    @Value("${psp.jawupay.http.read-timeout-ms:10000}")
    private int readTimeoutMs = 10000;

    @Value("${psp.jawupay.http.safe-retry.max-attempts:3}")
    private int safeRetryMaxAttempts = 3;

    @Value("${psp.jawupay.http.safe-retry.initial-delay-ms:500}")
    private long safeRetryInitialDelayMs = 500L;

    private RestClient restClient;
    private RetrySleeper retrySleeper = Thread::sleep;

    @PostConstruct
    void init() {
        validateHttpConfiguration();
        if (hmacSecret != null && !hmacSecret.isBlank()) {
            hmacSecretBytes = hmacSecret.trim().getBytes(StandardCharsets.UTF_8);
        }
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(this::signRequest)
                .requestFactory(createRequestFactory())
                .defaultStatusHandler(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, resp) -> {
                            String responseBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            log.error("PSP error provider=jawupay method={} uri={} status={} responseBody={}",
                                    req.getMethod(), req.getURI(), resp.getStatusCode(), responseBody);
                            throw new JawuPayPspException(
                                    "JawuPay PSP error: HTTP " + resp.getStatusCode()
                                            + " for " + req.getMethod() + " " + req.getURI());
                        })
                .build();
    }

    private ClientHttpResponse signRequest(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (hmacSecretBytes == null) {
            return execution.execute(request, body);
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String bodyStr = body != null && body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "";
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String signature = computeJawuPaySignature(timestamp, nonce, bodyStr, method, path);
        log.debug("JawuPay outgoing auth method={} uri={} signedPath={} timestamp={} nonce={} bodyLength={}",
                request.getMethod(), request.getURI(), path, timestamp, nonce, bodyStr.length());
        HttpHeaders newHeaders = new HttpHeaders();
        newHeaders.putAll(request.getHeaders());
        newHeaders.set("X-API-KEY", apiKey);
        newHeaders.set("X-TIMESTAMP", timestamp);
        newHeaders.set("X-NONCE", nonce);
        newHeaders.set("X-SIGNATURE", signature);
        return execution.execute(new HttpRequestWrapper(request) {
            @Override
            public HttpHeaders getHeaders() {
                return newHeaders;
            }
        }, body);
    }

    /**
     * Calcule la signature HMAC-SHA256 JawuPay.
     * Payload signé : {METHOD}:{path}:{timestamp}:{nonce}:{body}
     * Résultat : hex lowercase
     */
    String computeJawuPaySignature(String timestamp, String nonce, String body, String method, String path) {
        String payload = method + ":" + path + ":" + timestamp + ":" + nonce + ":" + body;
        return computeHmacHex(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Vérifie une signature HMAC-SHA256 JawuPay (constant-time comparison).
     */
    boolean verifyJawuPaySignature(String timestamp, String nonce, String body, String expectedSignature) {
        if (hmacSecretBytes == null || expectedSignature == null) {
            return false;
        }
        String computed = computeJawuPaySignature(timestamp, nonce, body, "POST", "");
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String computeHmacHex(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecretBytes, "HmacSHA256"));
            byte[] result = mac.doFinal(payload);
            StringBuilder sb = new StringBuilder(result.length * 2);
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to compute JawuPay HMAC signature", ex);
        }
    }

    JdkClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return requestFactory;
    }

    // -----------------------------------------------------------------------
    // Payment quote
    // -----------------------------------------------------------------------

    @Override
    public JawuPayQuoteResponse createQuote(JawuPayQuoteRequest request) {
        log.info("PSP request provider=jawupay endpoint=POST /api/v1/payment/quote clientId={} country={} paymentMethodId={} amountMinor={}",
                safeValue(request.clientId()),
                safeValue(request.country()),
                safeValue(request.paymentMethodId()),
                request.amount());
        if (mockEnabled) {
            JawuPayQuoteResponse response = new JawuPayQuoteResponse(
                    UUID.randomUUID(),
                    request.amount(),
                    0L,
                    request.amount(),
                    OffsetDateTime.now().plusMinutes(5)
            );
            log.info("PSP response provider=jawupay mode=mock endpoint=POST /api/v1/payment/quote quoteId={} amountMinor={} totalAmountMinor={} expiresAt={}",
                    safeUuid(response.quoteId()),
                    response.amount(),
                    response.totalAmount(),
                    response.expiresAt());
            return response;
        }
        JawuPayQuoteResponse response = executeWithoutRetry(
                "POST /api/v1/payment/quote",
                "clientId",
                request.clientId(),
                () -> doCreateQuoteRequest(request)
        );
        log.info("PSP response provider=jawupay mode=live endpoint=POST /api/v1/payment/quote quoteId={} amountMinor={} totalAmountMinor={} expiresAt={}",
                safeUuid(response == null ? null : response.quoteId()),
                response == null ? null : response.amount(),
                response == null ? null : response.totalAmount(),
                response == null ? null : response.expiresAt());
        return response;
    }

    // -----------------------------------------------------------------------
    // Payment confirm
    // -----------------------------------------------------------------------

    @Override
    public JawuPayConfirmResponse confirmPayment(JawuPayConfirmRequest request, String idempotencyKey) {
        log.info("PSP request provider=jawupay endpoint=POST /api/v1/payment/confirm quoteId={} currency={} payerMsisdnPresent={} payerCardPresent={} idempotencyKey={}",
                safeUuid(request.quoteId()),
                safeValue(request.currency()),
                request.payerInfo() != null && request.payerInfo().msisdn() != null,
                request.payerInfo() != null && request.payerInfo().cardNumber() != null,
                safeValue(idempotencyKey));
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
            log.info("PSP response provider=jawupay mode=mock endpoint=POST /api/v1/payment/confirm paymentId={} status={} provider={} redirectPresent={}",
                    safeUuid(response.paymentId()),
                    safeValue(response.status()),
                    safeValue(response.pspProvider()),
                    response.redirectUrl() != null);
            return response;
        }
        JawuPayConfirmResponse response = executeWithoutRetry(
                "POST /api/v1/payment/confirm",
                "quoteId",
                safeUuid(request.quoteId()),
                () -> doConfirmPaymentRequest(request, idempotencyKey)
        );
        log.info("PSP response provider=jawupay mode=live endpoint=POST /api/v1/payment/confirm paymentId={} status={} provider={} redirectPresent={}",
                safeUuid(response == null ? null : response.paymentId()),
                safeValue(response == null ? null : response.status()),
                safeValue(response == null ? null : response.pspProvider()),
                response != null && response.redirectUrl() != null);
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
            log.info("PSP response provider=jawupay mode=mock endpoint=GET /api/v1/payment/{} paymentId={} status={} provider={} amountMinor={} currency={}",
                    paymentId,
                    safeUuid(response.paymentId()),
                    safeValue(response.status()),
                    safeValue(response.pspProvider()),
                    response.amount(),
                    safeValue(response.currency()));
            return response;
        }
        JawuPayPaymentResponse response = executeWithSafeRetry(
                "GET /api/v1/payment/{paymentId}",
                "paymentId",
                paymentId,
                () -> doGetPaymentRequest(paymentId)
        );
        log.info("PSP response provider=jawupay mode=live endpoint=GET /api/v1/payment/{} paymentId={} status={} provider={} amountMinor={} currency={}",
                paymentId,
                safeUuid(response == null ? null : response.paymentId()),
                safeValue(response == null ? null : response.status()),
                safeValue(response == null ? null : response.pspProvider()),
                response == null ? null : response.amount(),
                safeValue(response == null ? null : response.currency()));
        return response;
    }

    // -----------------------------------------------------------------------
    // Payout
    // -----------------------------------------------------------------------

    @Override
    public JawuPayCreatePayoutResponse createPayout(JawuPayCreatePayoutRequest request) {
        log.info("PSP request provider=jawupay endpoint=POST /api/v1/payout clientId={} country={} paymentMethodId={} amountMinor={} beneficiaryPresent={}",
                safeValue(request.clientId()),
                safeValue(request.country()),
                safeValue(request.paymentMethodId()),
                request.amount(),
                request.beneficiaryRef() != null);
        if (mockEnabled) {
            JawuPayCreatePayoutResponse response = new JawuPayCreatePayoutResponse(
                    UUID.randomUUID(), "PENDING", "MOCK_PSP", "MOCK-PAYOUT-REF-" + UUID.randomUUID());
            log.info("PSP response provider=jawupay mode=mock endpoint=POST /api/v1/payout payoutId={} status={} provider={}",
                    safeUuid(response.payoutId()),
                    safeValue(response.status()),
                    safeValue(response.pspProvider()));
            return response;
        }
        JawuPayCreatePayoutResponse response = executeWithoutRetry(
                "POST /api/v1/payout",
                "clientId",
                request.clientId(),
                () -> doCreatePayoutRequest(request)
        );
        log.info("PSP response provider=jawupay mode=live endpoint=POST /api/v1/payout payoutId={} status={} provider={}",
                safeUuid(response == null ? null : response.payoutId()),
                safeValue(response == null ? null : response.status()),
                safeValue(response == null ? null : response.pspProvider()));
        return response;
    }

    @Override
    public JawuPayPayoutDetailsResponse getPayout(String payoutId) {
        log.info("PSP request provider=jawupay endpoint=GET /api/v1/payout/{}", payoutId);
        if (mockEnabled) {
            JawuPayPayoutDetailsResponse response = new JawuPayPayoutDetailsResponse(
                    UUID.fromString(payoutId), null, "PENDING", 0L, "XOF",
                    null, null, "MOCK_PSP", null, OffsetDateTime.now(), OffsetDateTime.now());
            log.info("PSP response provider=jawupay mode=mock endpoint=GET /api/v1/payout/{} payoutId={} status={} provider={} amountMinor={} currency={}",
                    payoutId,
                    safeUuid(response.payoutId()),
                    safeValue(response.status()),
                    safeValue(response.pspProvider()),
                    response.amount(),
                    safeValue(response.currency()));
            return response;
        }
        JawuPayPayoutDetailsResponse response = executeWithSafeRetry(
                "GET /api/v1/payout/{payoutId}",
                "payoutId",
                payoutId,
                () -> doGetPayoutRequest(payoutId)
        );
        log.info("PSP response provider=jawupay mode=live endpoint=GET /api/v1/payout/{} payoutId={} status={} provider={} amountMinor={} currency={}",
                payoutId,
                safeUuid(response == null ? null : response.payoutId()),
                safeValue(response == null ? null : response.status()),
                safeValue(response == null ? null : response.pspProvider()),
                response == null ? null : response.amount(),
                safeValue(response == null ? null : response.currency()));
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
            log.info("PSP response provider=jawupay mode=mock endpoint=GET /api/v1/payment-methods country={} methodCount={}",
                    safeValue(country),
                    response.size());
            return response;
        }
        List<JawuPayPaymentMethod> response = executeWithoutRetry(
                "GET /api/v1/payment-methods",
                "country",
                country,
                () -> doGetPaymentMethodsRequest(country)
        );
        log.info("PSP response provider=jawupay mode=live endpoint=GET /api/v1/payment-methods country={} methodCount={}",
                safeValue(country),
                response == null ? 0 : response.size());
        return response;
    }

    JawuPayQuoteResponse doCreateQuoteRequest(JawuPayQuoteRequest request) {
        JawuPayApiResponse<JawuPayQuoteResponse> response = restClient.post()
                .uri("/api/v1/payment/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null ? response.data() : null;
    }

    JawuPayConfirmResponse doConfirmPaymentRequest(JawuPayConfirmRequest request, String idempotencyKey) {
        JawuPayApiResponse<JawuPayConfirmResponse> response = restClient.post()
                .uri("/api/v1/payment/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null ? response.data() : null;
    }

    JawuPayPaymentResponse doGetPaymentRequest(String paymentId) {
        JawuPayApiResponse<JawuPayPaymentResponse> response = restClient.get()
                .uri("/api/v1/payment/{paymentId}", paymentId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null ? response.data() : null;
    }

    JawuPayCreatePayoutResponse doCreatePayoutRequest(JawuPayCreatePayoutRequest request) {
        JawuPayApiResponse<JawuPayCreatePayoutResponse> response = restClient.post()
                .uri("/api/v1/payout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null ? response.data() : null;
    }

    JawuPayPayoutDetailsResponse doGetPayoutRequest(String payoutId) {
        JawuPayApiResponse<JawuPayPayoutDetailsResponse> response = restClient.get()
                .uri("/api/v1/payout/{payoutId}", payoutId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null ? response.data() : null;
    }

    List<JawuPayPaymentMethod> doGetPaymentMethodsRequest(String country) {
        JawuPayApiResponse<List<JawuPayPaymentMethod>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/payment-methods")
                        .queryParam("country", country)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response != null && response.data() != null ? response.data() : List.of();
    }

    private <T> T executeWithoutRetry(String endpoint,
                                      String identifierLabel,
                                      String identifierValue,
                                      Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ResourceAccessException ex) {
            throw translateCommunicationException(endpoint, identifierLabel, identifierValue, ex, 1, 1, false);
        }
    }

    private <T> T executeWithSafeRetry(String endpoint,
                                       String identifierLabel,
                                       String identifierValue,
                                       Supplier<T> supplier) {
        long retryDelayMs = safeRetryInitialDelayMs;
        for (int attempt = 1; attempt <= safeRetryMaxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (ResourceAccessException ex) {
                boolean timeout = isTimeoutException(ex);
                if (!timeout) {
                    throw translateCommunicationException(endpoint, identifierLabel, identifierValue, ex, attempt, safeRetryMaxAttempts, false);
                }
                if (attempt >= safeRetryMaxAttempts) {
                    throw translateCommunicationException(endpoint, identifierLabel, identifierValue, ex, attempt, safeRetryMaxAttempts, false);
                }
                log.warn("PSP timeout provider=jawupay endpoint={} {}={} attempt={} maxAttempts={} retryingInMs={}",
                        endpoint,
                        identifierLabel,
                        safeValue(identifierValue),
                        attempt,
                        safeRetryMaxAttempts,
                        retryDelayMs);
                sleepBeforeRetry(endpoint, identifierLabel, identifierValue, retryDelayMs);
                retryDelayMs = nextRetryDelay(retryDelayMs);
            }
        }
        throw new IllegalStateException("Unexpected retry loop exit for " + endpoint);
    }

    private void sleepBeforeRetry(String endpoint,
                                  String identifierLabel,
                                  String identifierValue,
                                  long retryDelayMs) {
        if (retryDelayMs <= 0) {
            return;
        }
        try {
            retrySleeper.sleep(retryDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PSPCommunicationException(
                    "JawuPay retry interrupted for " + endpoint + " " + identifierLabel + "=" + safeValue(identifierValue),
                    ex
            );
        }
    }

    private long nextRetryDelay(long currentDelayMs) {
        if (currentDelayMs <= 0) {
            return 0L;
        }
        return currentDelayMs > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : currentDelayMs * 2;
    }

    private PSPCommunicationException translateCommunicationException(String endpoint,
                                                                     String identifierLabel,
                                                                     String identifierValue,
                                                                     ResourceAccessException ex,
                                                                     int attempt,
                                                                     int maxAttempts,
                                                                     boolean retryScheduled) {
        boolean timeout = isTimeoutException(ex);
        if (timeout) {
            log.warn("PSP timeout provider=jawupay endpoint={} {}={} attempt={} maxAttempts={} retryScheduled={}",
                    endpoint,
                    identifierLabel,
                    safeValue(identifierValue),
                    attempt,
                    maxAttempts,
                    retryScheduled);
            return new PSPCommunicationException(
                    "JawuPay timeout for " + endpoint + " " + identifierLabel + "=" + safeValue(identifierValue),
                    ex
            );
        }
        return new PSPCommunicationException(
                "JawuPay communication failure for " + endpoint + " " + identifierLabel + "=" + safeValue(identifierValue),
                ex
        );
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof InterruptedIOException
                    || current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void validateHttpConfiguration() {
        if (connectTimeoutMs <= 0) {
            throw new IllegalStateException("psp.jawupay.http.connect-timeout-ms must be positive");
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalStateException("psp.jawupay.http.read-timeout-ms must be positive");
        }
        if (safeRetryMaxAttempts <= 0) {
            throw new IllegalStateException("psp.jawupay.http.safe-retry.max-attempts must be positive");
        }
        if (safeRetryInitialDelayMs < 0) {
            throw new IllegalStateException("psp.jawupay.http.safe-retry.initial-delay-ms must not be negative");
        }
    }

    private String safeUuid(UUID value) {
        return value == null ? "n/a" : value.toString();
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long delayMillis) throws InterruptedException;
    }

    public static final class PSPCommunicationException extends RuntimeException {

        public PSPCommunicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
