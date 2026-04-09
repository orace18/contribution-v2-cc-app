package com.ccapp.circles.controller;

import com.ccapp.circles.dto.*;
import com.ccapp.circlesocial.dto.CircleActivityResponse;
import com.ccapp.circlesocial.dto.CircleProgressResponse;
import com.ccapp.circlesocial.dto.CircleScoreResponse;
import com.ccapp.circlesocial.service.CircleActivityService;
import com.ccapp.circlesocial.service.CircleProgressService;
import com.ccapp.circlesocial.service.CircleScoreService;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.service.CircleMeetingPolicyService;
import com.ccapp.circles.service.CircleMobileQueryService;
import com.ccapp.circles.service.CircleService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.security.SecurityUtils;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/circles")
@RequiredArgsConstructor
@Validated
@Tag(name = "Circles", description = "Gestion des cercles et de leurs membres")
@SecurityRequirement(name = "bearerAuth")
public class CircleController {

    private final CircleService circleService;
    private final CircleMobileQueryService circleMobileQueryService;
    private final CircleMeetingPolicyService circleMeetingPolicyService;
    private final CircleActivityService circleActivityService;
    private final CircleProgressService circleProgressService;
    private final CircleScoreService circleScoreService;

    @PostMapping
    @Operation(summary = "Creer un cercle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes - cercle cree", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Regle metier invalide (MEMBER_NOT_ACTIVE, cercle complet)", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Utilisateur deja dans un cercle actif", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleResponse>> createCircle(@Valid @RequestBody CreateCircleRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success("Circle created", circleService.createCircle(accountHolderId, request)));
    }

    @PostMapping("/{circleId}/join")
    @Operation(summary = "Rejoindre un cercle actif")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes - cercle rejoint", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Regle metier invalide (ex: cercle plein)", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle ou utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflit (deja membre/deja dans un cercle actif)", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleMemberResponse>> joinCircle(@PathVariable UUID circleId,
                                                                           @Valid @RequestBody JoinCircleRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success("Circle joined", circleService.joinCircle(accountHolderId, circleId, request)));
    }

    @GetMapping
    @Operation(summary = "Lister les cercles de l'utilisateur authentifie")
    public ResponseEntity<CommonResponse<PageResponse<CircleListItemResponse>>> listUserCircles(
            @RequestParam(required = false) CircleStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "User circles",
                circleMobileQueryService.listUserCircles(accountHolderId, status, search, page, size)
        ));
    }

    @GetMapping("/my-circle")
    @Operation(summary = "Recuperer le cercle actif de l'utilisateur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun cercle actif", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<MyCircleResponse>> myCircle() {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success("My active circle", circleService.getMyCircle(accountHolderId)));
    }

    @GetMapping("/{circleId}")
    @Operation(summary = "Recuperer le detail canonique d'un cercle pour mobile")
    public ResponseEntity<CommonResponse<CircleDetailResponse>> circleDetail(@PathVariable UUID circleId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle detail",
                circleMobileQueryService.getCircleDetail(requesterAccountHolderId, circleId)
        ));
    }

    @GetMapping("/{circleId}/members")
    @Operation(summary = "Lister les membres d'un cercle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<CircleMemberResponse>>> listMembers(@PathVariable UUID circleId) {
        return ResponseEntity.ok(CommonResponse.success("Circle members", circleService.listMembers(circleId)));
    }

    @PatchMapping("/{circleId}/meeting-policy")
    @Operation(summary = "Mettre a jour la cadence canonique des reunions du cercle")
    public ResponseEntity<CommonResponse<CircleMeetingPolicyResponse>> updateMeetingPolicy(@PathVariable UUID circleId,
                                                                                           @Valid @RequestBody UpdateCircleMeetingPolicyRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle meeting policy updated",
                circleMeetingPolicyService.updatePolicy(requesterAccountHolderId, circleId, request)
        ));
    }

    @DeleteMapping("/{circleId}/members/{memberAccountHolderId}")
    @Operation(summary = "Retirer un membre du cercle (proprietaire legacy uniquement)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membre retire"),
            @ApiResponse(responseCode = "400", description = "Regle metier invalide (CIRCLE_LOCKED_BY_ACTIVE_LOAN, circle owner cannot remove themselves)"),
            @ApiResponse(responseCode = "403", description = "Acces refuse — pas le createur du cercle"),
            @ApiResponse(responseCode = "404", description = "Cercle ou membre introuvable")
    })
    public ResponseEntity<CommonResponse<Void>> removeMember(@PathVariable UUID circleId,
                                                             @PathVariable UUID memberAccountHolderId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        circleService.removeMember(accountHolderId, circleId, memberAccountHolderId);
        return ResponseEntity.ok(CommonResponse.success("Member removed", null));
    }

    @GetMapping("/{circleId}/members/{memberAccountHolderId}")
    @Operation(summary = "Recuperer le profil d'un membre dans un cercle")
    public ResponseEntity<CommonResponse<CircleMemberProfileResponse>> memberProfile(@PathVariable UUID circleId,
                                                                                      @PathVariable UUID memberAccountHolderId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle member profile",
                circleMobileQueryService.getMemberProfile(requesterAccountHolderId, circleId, memberAccountHolderId)
        ));
    }

    @GetMapping("/{circleId}/members/{memberAccountHolderId}/financials")
    @Operation(summary = "Recuperer les donnees financieres d'un membre dans un cercle")
    public ResponseEntity<CommonResponse<CircleMemberFinancialsResponse>> memberFinancials(@PathVariable UUID circleId,
                                                                                            @PathVariable UUID memberAccountHolderId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle member financials",
                circleMobileQueryService.getMemberFinancials(requesterAccountHolderId, circleId, memberAccountHolderId)
        ));
    }

    @GetMapping("/{circleId}/members/{memberAccountHolderId}/history")
    @Operation(summary = "Recuperer l'historique d'un membre dans un cercle")
    public ResponseEntity<CommonResponse<PageResponse<CircleMemberHistoryItemResponse>>> memberHistory(@PathVariable UUID circleId,
                                                                                                         @PathVariable UUID memberAccountHolderId,
                                                                                                         @RequestParam(defaultValue = "0") Integer page,
                                                                                                         @RequestParam(defaultValue = "20") Integer size) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle member history",
                circleMobileQueryService.getMemberHistory(requesterAccountHolderId, circleId, memberAccountHolderId, page, size)
        ));
    }

    @PostMapping("/{circleId}/members/{memberAccountHolderId}/interactions")
    @Operation(summary = "Enregistrer une interaction communautaire sur un membre")
    public ResponseEntity<CommonResponse<CircleMemberInteractionResponse>> interactWithMember(@PathVariable UUID circleId,
                                                                                                @PathVariable UUID memberAccountHolderId,
                                                                                                @Valid @RequestBody CircleMemberInteractionRequest request) {
        UUID senderAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Circle member interaction created",
                circleMobileQueryService.createInteraction(senderAccountHolderId, circleId, memberAccountHolderId, request.type())
        ));
    }

    @GetMapping("/{circleId}/participants")
    @Operation(summary = "Lister les participants d'un cercle (membres, coach, advisors)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleParticipantsResponse>> participants(@PathVariable UUID circleId) {
        return ResponseEntity.ok(CommonResponse.success("Circle participants", circleService.getParticipants(circleId)));
    }

    @GetMapping("/{circleId}/activity")
    @Operation(summary = "Lister les activites sociales d'un cercle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<CircleActivityResponse>>> activity(@PathVariable UUID circleId) {
        return ResponseEntity.ok(CommonResponse.success("Circle activity", circleActivityService.getCircleActivity(circleId)));
    }

    @GetMapping("/{circleId}/progress")
    @Operation(summary = "Consulter la progression globale du cercle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleProgressResponse>> progress(@PathVariable UUID circleId) {
        return ResponseEntity.ok(CommonResponse.success("Circle progress", circleProgressService.getCircleProgress(circleId)));
    }

    @GetMapping("/{circleId}/score")
    @Operation(summary = "Consulter le score global du cercle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cercle introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleScoreResponse>> score(@PathVariable UUID circleId) {
        return ResponseEntity.ok(CommonResponse.success("Circle score", circleScoreService.getScore(circleId)));
    }
}
