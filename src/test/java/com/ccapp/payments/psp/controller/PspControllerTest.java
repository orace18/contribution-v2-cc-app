package com.ccapp.payments.psp.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.service.JawupayService;
import com.ccapp.payments.psp.service.JawuPayWebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PspControllerTest {

    @Mock
    private JawupayService jawupayService;
    @Mock
    private JawuPayWebhookService jawuPayWebhookService;

    @InjectMocks
    private PspController controller;

    @Test
    void jawuPayWebhook_missingPaymentAndPayoutIds_returnsBadRequest() {
        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", null, null, null);

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("paymentId or payoutId is required");
        verifyNoInteractions(jawuPayWebhookService);
    }

    @Test
    void jawuPayWebhook_usesRemoteAddrAndIgnoresForwardedHeader() {
        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", "pay-123", null, null);

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", "1.1.1.1, 2.2.2.2"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(jawuPayWebhookService).processWebhook(request, "9.9.9.9");
    }

    @Test
    void jawuPayWebhook_accessDenied_returnsForbidden() {
        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", "pay-123", null, null);
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
    void jawuPayWebhook_processingError_returnsOkForReconciliation() {
        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", "pay-123", null, null);
        doThrow(new RuntimeException("boom"))
                .when(jawuPayWebhookService).processWebhook(request, "9.9.9.9");

        ResponseEntity<CommonResponse<Void>> response =
                controller.jawuPayWebhook(request, remoteRequest("9.9.9.9", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).contains("reconciliation");
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
