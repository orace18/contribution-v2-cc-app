package com.ccapp.seedcapital.savings.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.seedcapital.savings.dto.CircleSavingsGateResponse;
import com.ccapp.seedcapital.savings.dto.ConfirmSavingsContributionRequest;
import com.ccapp.seedcapital.savings.dto.CreateSavingsCycleRequest;
import com.ccapp.seedcapital.savings.dto.MemberSavingsContributionResponse;
import com.ccapp.seedcapital.savings.dto.SavingsCycleResponse;
import com.ccapp.seedcapital.savings.service.ImfSavingsGateService;
import com.ccapp.seedcapital.savings.service.MemberSavingsContributionService;
import com.ccapp.seedcapital.savings.service.SavingsCycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/circles/{circleId}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Circle Savings", description = "Cycles et discipline d'epargne canonique du cercle")
@SecurityRequirement(name = "bearerAuth")
public class CircleSavingsController {

    private final SavingsCycleService savingsCycleService;
    private final MemberSavingsContributionService memberSavingsContributionService;
    private final ImfSavingsGateService imfSavingsGateService;

    @PostMapping("/savings-cycles")
    @Operation(summary = "Creer un cycle canonique d'epargne de cercle")
    public ResponseEntity<CommonResponse<SavingsCycleResponse>> createCycle(@PathVariable UUID circleId,
                                                                            @Valid @RequestBody CreateSavingsCycleRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Savings cycle created",
                savingsCycleService.createCycle(requesterAccountHolderId, circleId, request)
        ));
    }

    @GetMapping("/savings-cycles")
    @Operation(summary = "Lister les cycles canoniques d'epargne d'un cercle")
    public ResponseEntity<CommonResponse<List<SavingsCycleResponse>>> listCycles(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Savings cycles",
                savingsCycleService.listCycles(requesterAccountHolderId, circleId)
        ));
    }

    @GetMapping("/savings-gate")
    @Operation(summary = "Consulter la gate canonique IMF/epargne du cercle")
    public ResponseEntity<CommonResponse<CircleSavingsGateResponse>> getSavingsGate(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle savings gate",
                imfSavingsGateService.getGate(requesterAccountHolderId, circleId)
        ));
    }

    @PostMapping("/savings-cycles/{cycleId}/contributions/me/confirm")
    @Operation(summary = "Confirmer ma contribution d'epargne IMF sur un cycle de cercle")
    public ResponseEntity<CommonResponse<MemberSavingsContributionResponse>> confirmMyContribution(@PathVariable UUID circleId,
                                                                                                   @PathVariable UUID cycleId,
                                                                                                   @Valid @RequestBody ConfirmSavingsContributionRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Savings contribution confirmed",
                memberSavingsContributionService.confirmMyContribution(requesterAccountHolderId, circleId, cycleId, request)
        ));
    }

    @GetMapping("/savings-cycles/{cycleId}/contributions")
    @Operation(summary = "Lister les contributions canoniques d'un cycle d'epargne")
    public ResponseEntity<CommonResponse<List<MemberSavingsContributionResponse>>> listContributions(@PathVariable UUID circleId,
                                                                                                      @PathVariable UUID cycleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Savings contributions",
                memberSavingsContributionService.listCycleContributions(requesterAccountHolderId, circleId, cycleId)
        ));
    }
}
