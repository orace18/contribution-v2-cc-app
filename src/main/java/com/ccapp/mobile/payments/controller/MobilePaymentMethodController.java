package com.ccapp.mobile.payments.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.mobile.payments.dto.MobilePaymentMethodsResponse;
import com.ccapp.mobile.payments.service.MobilePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/payment-methods")
@RequiredArgsConstructor
@Tag(name = "Mobile Payment Methods", description = "Mobile payment method catalog")
@SecurityRequirement(name = "bearerAuth")
public class MobilePaymentMethodController {

    private final MobilePaymentService mobilePaymentService;

    @GetMapping
    @Operation(summary = "List supported payment methods for a country")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "400", description = "Invalid country", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobilePaymentMethodsResponse>> list(@RequestParam("country") String country) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Payment methods",
                mobilePaymentService.getPaymentMethods(country)
        ));
    }
}
