package com.ccapp.subscription.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.subscription.dto.InitiateSubscriptionRequest;
import com.ccapp.subscription.dto.InitiateSubscriptionResponse;
import com.ccapp.subscription.dto.SubscriptionPlanResponse;
import com.ccapp.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Gestion des plans et de l'initiation d'abonnement")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    @Operation(summary = "Lister les plans actifs par pays")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes (endpoint public)", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parametre invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<SubscriptionPlanResponse>>> plans(@RequestParam String country) {
        return ResponseEntity.ok(CommonResponse.success("Subscription plans", subscriptionService.getPlans(country)));
    }

    @PostMapping("/initiate")
    @Operation(summary = "Initier un abonnement")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation/regle metier invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "User/plan introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflit fonctionnel", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<InitiateSubscriptionResponse>> initiate(
            @Valid @RequestBody InitiateSubscriptionRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Subscription initiated",
                subscriptionService.initiate(SecurityUtils.currentUserId(), request)));
    }
}
