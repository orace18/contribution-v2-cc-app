package com.ccapp.payments.psp.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayPaymentMethod;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.service.JawuPayWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PspControllerTest {

    @Mock
    private JawuPayWebhookService jawuPayWebhookService;

    @Mock
    private JawuPayPspClient jawuPayPspClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PspController controller;

    @Test
    void jawuPayPaymentMethods_returnsMethods() {
        when(jawuPayPspClient.getPaymentMethods("CI"))
                .thenReturn(List.of(new JawuPayPaymentMethod("MOBILE_MONEY_MTN", "MTN Mobile Money")));

        ResponseEntity<CommonResponse<List<JawuPayPaymentMethod>>> response =
                controller.jawuPayPaymentMethods("ci");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).id()).isEqualTo("MOBILE_MONEY_MTN");
        verify(jawuPayPspClient).getPaymentMethods("CI");
    }

    @Test
    void jawuPayPaymentMethods_invalidCountry_throwsBusinessException() {
        assertThatThrownBy(() -> controller.jawuPayPaymentMethods("C"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("country must be ISO code length 2 or 3");

        verifyNoInteractions(jawuPayPspClient);
    }

    @Test
    void jawuPayWebhook_missingReferences_returnsBadRequest() {
        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", null, null, null);
        doThrow(new BusinessException("paymentId or payoutId or providerReference is required"))
                .when(jawuPayWebhookService).processWebhook(eq(request), any(byte[].class), anyString(), eq("9.9.9.9"));

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(rawBody(request), "sig", remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("paymentId or payoutId or providerReference is required");
    }

    @Test
    void jawuPayWebhook_providerReferenceOnly_isAccepted() {
        JawuPayWebhookRequest request = validRequest();
        byte[] rawBody = rawBody(request);

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(rawBody, "sig", remoteRequest("9.9.9.9", "1.1.1.1, 2.2.2.2"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(jawuPayWebhookService).processWebhook(eq(request), any(byte[].class), eq("sig"), eq("1.1.1.1"));
    }

    @Test
    void jawuPayWebhook_xRealIpFallback_isAccepted() {
        JawuPayWebhookRequest request = validRequest();
        byte[] rawBody = rawBody(request);
        MockHttpServletRequest httpRequest = remoteRequest("9.9.9.9", null);
        httpRequest.addHeader("X-Real-IP", "3.3.3.3");

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(rawBody, "sig", httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(jawuPayWebhookService).processWebhook(eq(request), any(byte[].class), eq("sig"), eq("3.3.3.3"));
    }

    @Test
    void jawuPayWebhook_accessDenied_returnsForbidden() {
        JawuPayWebhookRequest request = validRequest();
        byte[] rawBody = rawBody(request);
        doThrow(new AccessDeniedException("Invalid JawuPay webhook signature"))
                .when(jawuPayWebhookService).processWebhook(eq(request), any(byte[].class), eq("sig"), eq("9.9.9.9"));

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(rawBody, "sig", remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Invalid JawuPay webhook signature");
    }

    @Test
    void jawuPayWebhook_businessValidation_returnsBadRequest() {
        JawuPayWebhookRequest request = validRequest();
        byte[] rawBody = rawBody(request);
        doThrow(new BusinessException("Only JawuPay provider is accepted"))
                .when(jawuPayWebhookService).processWebhook(eq(request), any(byte[].class), eq("sig"), eq("9.9.9.9"));

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(rawBody, "sig", remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("JawuPay");
    }

    @Test
    void jawuPayWebhook_duplicateWebhook_returnsConflict() {
        JawuPayWebhookRequest request = validRequest();
        byte[] rawBody = rawBody(request);
        doThrow(new ConflictException("Duplicate JawuPay webhook event rejected"))
                .when(jawuPayWebhookService).processWebhook(eq(request), any(byte[].class), eq("sig"), eq("9.9.9.9"));

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(rawBody, "sig", remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Duplicate");
    }

    @Test
    void jawuPayWebhook_processingError_returnsOkForReconciliation() {
        JawuPayWebhookRequest request = validRequest();
        byte[] rawBody = rawBody(request);
        doThrow(new RuntimeException("boom"))
                .when(jawuPayWebhookService).processWebhook(eq(request), any(byte[].class), eq("sig"), eq("9.9.9.9"));

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(rawBody, "sig", remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).contains("reconciliation");
    }

    @Test
    void jawuPayWebhook_invalidPayload_returnsBadRequest() {
        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook("{bad-json".getBytes(), "sig", remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Invalid JawuPay webhook payload");
        verifyNoInteractions(jawuPayWebhookService);
    }

    private JawuPayWebhookRequest validRequest() {
        return new JawuPayWebhookRequest(
                "JAWUPAY",
                "SUCCESS",
                null,
                null,
                null,
                "provider-ref-123",
                UUID.randomUUID(),
                150000L,
                "XOF",
                "evt-123"
        );
    }

    private MockHttpServletRequest remoteRequest(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private byte[] rawBody(JawuPayWebhookRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize webhook request", ex);
        }
    }
}
