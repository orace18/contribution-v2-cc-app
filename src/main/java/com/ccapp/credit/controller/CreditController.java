package com.ccapp.credit.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.service.CreditScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/credit")
@RequiredArgsConstructor
@Tag(name = "Credit", description = "Moteur de scoring credit")
@SecurityRequirement(name = "bearerAuth")
public class CreditController {

    private final CreditScoringService creditScoringService;

    @GetMapping("/eligibility/{accountHolderId}")
    @Operation(summary = "Evaluer l'eligibilite credit d'un account holder")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account holder ou cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CreditEligibilityResponse>> eligibility(@PathVariable UUID accountHolderId) {
        UUID actorUserId = SecurityUtils.currentPrincipal().userId() != null
                ? SecurityUtils.currentPrincipal().userId()
                : SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Credit eligibility evaluated",
                creditScoringService.getEligibility(actorUserId, accountHolderId)
        ));
    }
}
