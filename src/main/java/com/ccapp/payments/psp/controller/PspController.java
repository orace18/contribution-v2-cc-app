package com.ccapp.payments.psp.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.payments.psp.dto.JawupayCallbackRequest;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.payments.psp.service.JawupayService;
import com.ccapp.payments.psp.service.JawuPayWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/psp")
@RequiredArgsConstructor
@Tag(name = "PSP", description = "Callbacks et integration PSP")
public class PspController {

    private final JawupayService jawupayService;
    private final JawuPayWebhookService jawuPayWebhookService;

    @PostMapping("/jawupay/callback")
    @Operation(summary = "Recevoir un callback Jawupay (public)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes - callback traite", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Signature, replay, validation ou regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Operation PSP introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> jawupayCallback(
            @Parameter(description = "Signature HMAC du callback")
            @RequestHeader(name = "X-JAWUPAY-SIGNATURE", required = false) String signature,
            @Valid @RequestBody JawupayCallbackRequest request,
            HttpServletRequest httpRequest) {

        jawupayService.processCallback(request, signature, extractRemoteIp(httpRequest));
        return ResponseEntity.ok(CommonResponse.success("Callback processed"));
    }

    @PostMapping("/jawupay/webhook")
    @Operation(summary = "Recevoir un webhook JawuPay (public)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook traite", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalide ou operation introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "403", description = "IP non autorisee", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> jawuPayWebhook(
            @Valid @RequestBody JawuPayWebhookRequest request,
            HttpServletRequest httpRequest) {

        jawuPayWebhookService.processWebhook(request, extractRemoteIp(httpRequest));
        return ResponseEntity.ok(CommonResponse.success("Webhook processed"));
    }

    private String extractRemoteIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
