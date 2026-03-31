package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawuPayConfirmRequest;
import com.ccapp.payments.psp.dto.JawuPayCreatePayoutRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class JawuPayPspClientImplLoggingTest {

    private JawuPayPspClientImpl client;

    @BeforeEach
    void setUp() {
        client = new JawuPayPspClientImpl();
        ReflectionTestUtils.setField(client, "baseUrl",
                "https://api-gateway-dev.jawupay.com/orchestrator-service");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key-secret");
        ReflectionTestUtils.setField(client, "mockEnabled", true);
        client.init();
    }

    @Test
    void confirmPayment_logsSanitizedFieldsOnly(CapturedOutput output) {
        client.confirmPayment(
                new JawuPayConfirmRequest(
                        UUID.randomUUID(),
                        "XOF",
                        JawuPayConfirmRequest.PayerInfo.forMobileMoney("22501234567", "MTN")
                ),
                "idem-key-1"
        );

        String logs = output.getOut();
        assertThat(logs).contains("endpoint=POST /api/v1/payment/confirm");
        assertThat(logs).contains("payerMsisdnPresent=true");
        assertThat(logs).contains("paymentId=");
        assertThat(logs).contains("status=PENDING");
        assertThat(logs).doesNotContain("22501234567");
        assertThat(logs).doesNotContain("cardNumber");
        assertThat(logs).doesNotContain("body=");
    }

    @Test
    void createPayout_logsSanitizedFieldsOnly(CapturedOutput output) {
        client.createPayout(new JawuPayCreatePayoutRequest(
                50000L,
                "XOF",
                "CI",
                "MOBILE_MONEY_MTN",
                "client-1",
                "+22501234567",
                "MTN"
        ));

        String logs = output.getOut();
        assertThat(logs).contains("endpoint=POST /api/v1/payout");
        assertThat(logs).contains("beneficiaryPresent=true");
        assertThat(logs).contains("payoutId=");
        assertThat(logs).contains("status=PENDING");
        assertThat(logs).doesNotContain("+22501234567");
        assertThat(logs).doesNotContain("test-api-key-secret");
        assertThat(logs).doesNotContain("body=");
    }
}
