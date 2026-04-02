package com.ccapp.mobile.sponsor.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.mobile.sponsor.dto.SponsorLookupRequest;
import com.ccapp.mobile.sponsor.dto.SponsorLookupResponse;
import com.ccapp.mobile.sponsor.service.MobileSponsorLookupService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/sponsors")
@RequiredArgsConstructor
@Tag(name = "Mobile Sponsors", description = "Sponsor lookup during onboarding")
@SecurityRequirement(name = "bearerAuth")
public class MobileSponsorController {

    private final MobileSponsorLookupService sponsorLookupService;

    @PostMapping("/lookup")
    @Operation(summary = "Lookup a sponsor by phone number during onboarding")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lookup result", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<SponsorLookupResponse>> lookup(@Valid @RequestBody SponsorLookupRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Sponsor lookup",
                sponsorLookupService.lookup(accountHolderId, request)
        ));
    }
}
