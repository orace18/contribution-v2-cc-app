package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawuPayConfirmRequest;
import com.ccapp.payments.psp.dto.JawuPayConfirmResponse;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutRequest;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutResponse;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayPayoutDetailsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class JawuPayPspClientImplRetryTest {

    private static final String PAYMENT_ID = UUID.randomUUID().toString();
    private static final String PAYOUT_ID = UUID.randomUUID().toString();

    private TestableJawuPayPspClientImpl client;
    private List<Long> retryDelays;

    @BeforeEach
    void setUp() {
        client = new TestableJawuPayPspClientImpl();
        retryDelays = new ArrayList<>();

        ReflectionTestUtils.setField(client, "baseUrl",
                "https://api-gateway-dev.jawupay.com/orchestrator-service");
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "mockEnabled", false);
        ReflectionTestUtils.setField(client, "connectTimeoutMs", 5000);
        ReflectionTestUtils.setField(client, "readTimeoutMs", 10000);
        ReflectionTestUtils.setField(client, "safeRetryMaxAttempts", 3);
        ReflectionTestUtils.setField(client, "safeRetryInitialDelayMs", 500L);
        ReflectionTestUtils.setField(client, "retrySleeper",
                (JawuPayPspClientImpl.RetrySleeper) delayMillis -> retryDelays.add(delayMillis));

        client.init();
    }

    @Test
    void getPayment_retriesSafeReadWithExponentialBackoff(CapturedOutput output) {
        client.paymentFailures.add(timeout("Read timed out"));
        client.paymentFailures.add(timeout("Read timed out"));
        client.paymentResponse = paymentResponse(PAYMENT_ID);

        JawuPayPaymentResponse response = client.getPayment(PAYMENT_ID);

        assertThat(response.paymentId()).isEqualTo(UUID.fromString(PAYMENT_ID));
        assertThat(client.getPaymentAttempts).isEqualTo(3);
        assertThat(retryDelays).containsExactly(500L, 1000L);
        assertThat(output.getOut()).contains("endpoint=GET /api/v1/payment/{paymentId}");
        assertThat(output.getOut()).contains("paymentId=" + PAYMENT_ID);
        assertThat(output.getOut()).contains("attempt=1");
        assertThat(output.getOut()).contains("attempt=2");
    }

    @Test
    void getPayout_timeoutAfterMaxAttempts_throwsPspCommunicationException() {
        client.payoutFailures.add(timeout("Read timed out"));
        client.payoutFailures.add(timeout("Read timed out"));
        client.payoutFailures.add(timeout("Read timed out"));

        assertThatThrownBy(() -> client.getPayout(PAYOUT_ID))
                .isInstanceOf(JawuPayPspClientImpl.PSPCommunicationException.class)
                .hasMessageContaining("GET /api/v1/payout/{payoutId}")
                .hasMessageContaining(PAYOUT_ID);

        assertThat(client.getPayoutAttempts).isEqualTo(3);
        assertThat(retryDelays).containsExactly(500L, 1000L);
    }

    @Test
    void confirmPayment_timeoutIsNotRetried() {
        client.confirmFailure = timeout("Connect timed out");

        assertThatThrownBy(() -> client.confirmPayment(confirmRequest(), "idem-key-1"))
                .isInstanceOf(JawuPayPspClientImpl.PSPCommunicationException.class)
                .hasMessageContaining("POST /api/v1/payment/confirm");

        assertThat(client.confirmAttempts).isEqualTo(1);
        assertThat(retryDelays).isEmpty();
    }

    @Test
    void createPayout_timeoutIsNotRetried() {
        client.createPayoutFailure = timeout("Read timed out");

        assertThatThrownBy(() -> client.createPayout(createPayoutRequest()))
                .isInstanceOf(JawuPayPspClientImpl.PSPCommunicationException.class)
                .hasMessageContaining("POST /api/v1/payout");

        assertThat(client.createPayoutAttempts).isEqualTo(1);
        assertThat(retryDelays).isEmpty();
    }

    private ResourceAccessException timeout(String message) {
        return new ResourceAccessException(message, new SocketTimeoutException(message));
    }

    private JawuPayConfirmRequest confirmRequest() {
        return new JawuPayConfirmRequest(
                UUID.randomUUID(),
                "XOF",
                JawuPayConfirmRequest.PayerInfo.forMobileMoney("22501234567", "MTN")
        );
    }

    private JawuPayCreatePayoutRequest createPayoutRequest() {
        return new JawuPayCreatePayoutRequest(
                50000L,
                "XOF",
                "CI",
                "MOBILE_MONEY_MTN",
                "client-1",
                "+22501234567",
                "MTN"
        );
    }

    private JawuPayPaymentResponse paymentResponse(String paymentId) {
        return new JawuPayPaymentResponse(
                UUID.fromString(paymentId),
                null,
                "SUCCESS",
                150000L,
                "XOF",
                "CI",
                "MOBILE_MONEY_MTN",
                "JAWUPAY",
                "PAY-REF-1",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private JawuPayPayoutDetailsResponse payoutResponse(String payoutId) {
        return new JawuPayPayoutDetailsResponse(
                UUID.fromString(payoutId),
                null,
                "SUCCESS",
                150000L,
                "XOF",
                "CI",
                "MOBILE_MONEY_MTN",
                "JAWUPAY",
                "PAYOUT-REF-1",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private static final class TestableJawuPayPspClientImpl extends JawuPayPspClientImpl {

        private int getPaymentAttempts;
        private int getPayoutAttempts;
        private int confirmAttempts;
        private int createPayoutAttempts;

        private final Queue<ResourceAccessException> paymentFailures = new ArrayDeque<>();
        private final Queue<ResourceAccessException> payoutFailures = new ArrayDeque<>();

        private ResourceAccessException confirmFailure;
        private ResourceAccessException createPayoutFailure;

        private JawuPayPaymentResponse paymentResponse;
        private JawuPayPayoutDetailsResponse payoutResponse;

        @Override
        JawuPayPaymentResponse doGetPaymentRequest(String paymentId) {
            getPaymentAttempts++;
            if (!paymentFailures.isEmpty()) {
                throw paymentFailures.remove();
            }
            return paymentResponse;
        }

        @Override
        JawuPayPayoutDetailsResponse doGetPayoutRequest(String payoutId) {
            getPayoutAttempts++;
            if (!payoutFailures.isEmpty()) {
                throw payoutFailures.remove();
            }
            if (payoutResponse == null) {
                payoutResponse = new JawuPayPayoutDetailsResponse(
                        UUID.fromString(payoutId),
                        null,
                        "SUCCESS",
                        150000L,
                        "XOF",
                        "CI",
                        "MOBILE_MONEY_MTN",
                        "JAWUPAY",
                        "PAYOUT-REF-1",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                );
            }
            return payoutResponse;
        }

        @Override
        JawuPayConfirmResponse doConfirmPaymentRequest(JawuPayConfirmRequest request, String idempotencyKey) {
            confirmAttempts++;
            if (confirmFailure != null) {
                throw confirmFailure;
            }
            return new JawuPayConfirmResponse(
                    UUID.randomUUID(),
                    "PENDING",
                    "JAWUPAY",
                    "https://checkout.example/redirect",
                    null,
                    "PAY-REF-1"
            );
        }

        @Override
        JawuPayCreatePayoutResponse doCreatePayoutRequest(JawuPayCreatePayoutRequest request) {
            createPayoutAttempts++;
            if (createPayoutFailure != null) {
                throw createPayoutFailure;
            }
            return new JawuPayCreatePayoutResponse(
                    UUID.randomUUID(),
                    "PENDING",
                    "JAWUPAY",
                    "PAYOUT-REF-1"
            );
        }
    }
}
