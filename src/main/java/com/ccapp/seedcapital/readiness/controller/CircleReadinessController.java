package com.ccapp.seedcapital.readiness.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.seedcapital.readiness.dto.AssignCircleCoachRequest;
import com.ccapp.seedcapital.readiness.dto.CircleCoachAssignmentResponse;
import com.ccapp.seedcapital.readiness.dto.CircleMeetingResponse;
import com.ccapp.seedcapital.readiness.dto.CircleNetworkCoachingResponse;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalCreditCandidateResponse;
import com.ccapp.seedcapital.readiness.dto.CircleTrustScoreResponse;
import com.ccapp.seedcapital.readiness.dto.RecordCircleMeetingRequest;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalEligibilityResponse;
import com.ccapp.seedcapital.readiness.dto.UpdateCircleMeetingStatusRequest;
import com.ccapp.seedcapital.readiness.service.CircleCoachAssignmentService;
import com.ccapp.seedcapital.readiness.service.CircleMeetingService;
import com.ccapp.seedcapital.readiness.service.CircleTrustScoreService;
import com.ccapp.seedcapital.readiness.service.SeedCapitalCreditBridgeService;
import com.ccapp.seedcapital.readiness.service.SeedCapitalEligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/circles/{circleId}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Circle Readiness", description = "Readiness canonique du cercle pour le seed capital")
@SecurityRequirement(name = "bearerAuth")
public class CircleReadinessController {

    private final CircleMeetingService circleMeetingService;
    private final CircleCoachAssignmentService circleCoachAssignmentService;
    private final CircleTrustScoreService circleTrustScoreService;
    private final SeedCapitalEligibilityService seedCapitalEligibilityService;
    private final SeedCapitalCreditBridgeService seedCapitalCreditBridgeService;

    @PostMapping("/meetings")
    @Operation(summary = "Enregistrer une reunion de cercle")
    public ResponseEntity<CommonResponse<CircleMeetingResponse>> recordMeeting(@PathVariable UUID circleId,
                                                                               @Valid @RequestBody RecordCircleMeetingRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle meeting recorded",
                circleMeetingService.recordMeeting(requesterAccountHolderId, circleId, request)
        ));
    }

    @GetMapping("/meetings")
    @Operation(summary = "Lister les reunions d'un cercle")
    public ResponseEntity<CommonResponse<List<CircleMeetingResponse>>> listMeetings(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle meetings",
                circleMeetingService.listMeetings(requesterAccountHolderId, circleId)
        ));
    }

    @PatchMapping("/meetings/{meetingId}")
    @Operation(summary = "Mettre a jour le statut d'une reunion de cercle")
    public ResponseEntity<CommonResponse<CircleMeetingResponse>> updateMeetingStatus(@PathVariable UUID circleId,
                                                                                     @PathVariable UUID meetingId,
                                                                                     @Valid @RequestBody UpdateCircleMeetingStatusRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle meeting status updated",
                circleMeetingService.updateMeetingStatus(requesterAccountHolderId, circleId, meetingId, request)
        ));
    }

    @PutMapping("/coach-assignment")
    @Operation(summary = "Affecter explicitement un coach au cercle")
    public ResponseEntity<CommonResponse<CircleCoachAssignmentResponse>> assignCoach(@PathVariable UUID circleId,
                                                                                     @Valid @RequestBody AssignCircleCoachRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle coach assigned",
                circleCoachAssignmentService.assignCoach(requesterAccountHolderId, circleId, request)
        ));
    }

    @GetMapping("/coach-assignment")
    @Operation(summary = "Consulter l'affectation coach canonique du cercle")
    public ResponseEntity<CommonResponse<CircleCoachAssignmentResponse>> getCoachAssignment(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle coach assignment",
                circleCoachAssignmentService.getAssignment(requesterAccountHolderId, circleId)
        ));
    }

    @GetMapping("/network-coaching")
    @Operation(summary = "Consulter l'heritage canonique du coaching reseau")
    public ResponseEntity<CommonResponse<CircleNetworkCoachingResponse>> networkCoaching(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle network coaching",
                circleCoachAssignmentService.getNetworkCoaching(requesterAccountHolderId, circleId)
        ));
    }

    @GetMapping("/trust-score")
    @Operation(summary = "Consulter le trust score canonique du cercle")
    public ResponseEntity<CommonResponse<CircleTrustScoreResponse>> trustScore(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle trust score",
                circleTrustScoreService.getScore(requesterAccountHolderId, circleId)
        ));
    }

    @GetMapping("/seed-capital-eligibility")
    @Operation(summary = "Consulter l'eligibilite canonique seed capital du cercle")
    public ResponseEntity<CommonResponse<SeedCapitalEligibilityResponse>> seedCapitalEligibility(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Seed capital eligibility",
                seedCapitalEligibilityService.getEligibility(requesterAccountHolderId, circleId)
        ));
    }

    @GetMapping("/credit-candidate/me")
    @Operation(summary = "Consulter le pont canonique readiness vers candidature credit")
    public ResponseEntity<CommonResponse<SeedCapitalCreditCandidateResponse>> creditCandidate(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Seed capital credit candidate",
                seedCapitalCreditBridgeService.getCandidate(requesterAccountHolderId, circleId)
        ));
    }
}
