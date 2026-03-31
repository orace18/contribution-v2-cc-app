package com.ccapp.payments.psp.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.service.JawuPayWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/psp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PSP", description = "Callbacks et integration PSP")
public class PspController {

    private final JawuPayWebhookService jawuPayWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/jawupay/webhook")
    @Operation(summary = "Recevoir un webhook JawuPay (public)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook traite", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "403", description = "IP non autorisee", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> jawuPayWebhook(
            @RequestBody byte[] rawRequestBody,
            @RequestHeader(value = "X-Jawupay-Signature", required = false) String signatureHeader,
            HttpServletRequest httpRequest) {
        JawuPayWebhookRequest request = null;
        try {
            request = parseRequest(rawRequestBody);
            jawuPayWebhookService.processWebhook(request, rawRequestBody, signatureHeader, extractRemoteIp(httpRequest));
            return ResponseEntity.ok(CommonResponse.success("Webhook processed"));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommonResponse.error(ex.getMessage()));
        } catch (ConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(CommonResponse.error(ex.getMessage()));
        } catch (BusinessException ex) {
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("JawuPay webhook accepted with processing error provider={} paymentId={} payoutId={}",
                    request == null ? null : request.provider(),
                    request == null ? null : request.paymentId(),
                    request == null ? null : request.payoutId(),
                    ex);
            return ResponseEntity.ok(CommonResponse.success("Webhook accepted for reconciliation"));
        }
    }

    private String extractRemoteIp(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private JawuPayWebhookRequest parseRequest(byte[] rawRequestBody) {
        try {
            JawuPayWebhookRequest request = objectMapper.readValue(rawRequestBody, JawuPayWebhookRequest.class);
            if (request.provider() == null || request.provider().isBlank()) {
                throw new BusinessException("provider is required");
            }
            if (request.status() == null || request.status().isBlank()) {
                throw new BusinessException("status is required");
            }
            return request;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Invalid JawuPay webhook payload");
        }
    }
}
