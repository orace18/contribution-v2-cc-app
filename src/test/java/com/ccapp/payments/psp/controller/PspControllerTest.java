package com.ccapp.payments.psp.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.service.JawuPayWebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PspControllerTest {

    @Mock
    private JawuPayWebhookService jawuPayWebhookService;

    @InjectMocks
    private PspController controller;

    @Test
    void jawuPayWebhook_missingReferences_returnsBadRequest() {
        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", null, null, null);

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("paymentId or payoutId or providerReference is required");
        verifyNoInteractions(jawuPayWebhookService);
    }

    @Test
    void jawuPayWebhook_providerReferenceOnly_isAccepted() {
        JawuPayWebhookRequest request = validRequest();

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", "1.1.1.1, 2.2.2.2"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(jawuPayWebhookService).processWebhook(request, "9.9.9.9");
    }

    @Test
    void jawuPayWebhook_accessDenied_returnsForbidden() {
        JawuPayWebhookRequest request = validRequest();
        doThrow(new AccessDeniedException("blocked"))
                .when(jawuPayWebhookService).processWebhook(request, "9.9.9.9");

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("unauthorized IP");
    }

    @Test
    void jawuPayWebhook_businessValidation_returnsBadRequest() {
        JawuPayWebhookRequest request = validRequest();
        doThrow(new BusinessException("Only JawuPay provider is accepted"))
                .when(jawuPayWebhookService).processWebhook(request, "9.9.9.9");

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("JawuPay");
    }

    @Test
    void jawuPayWebhook_duplicateWebhook_returnsConflict() {
        JawuPayWebhookRequest request = validRequest();
        doThrow(new ConflictException("Duplicate JawuPay webhook event rejected"))
                .when(jawuPayWebhookService).processWebhook(request, "9.9.9.9");

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Duplicate");
    }

    @Test
    void jawuPayWebhook_processingError_returnsOkForReconciliation() {
        JawuPayWebhookRequest request = validRequest();
        doThrow(new RuntimeException("boom"))
                .when(jawuPayWebhookService).processWebhook(request, "9.9.9.9");

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).contains("reconciliation");
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
}
