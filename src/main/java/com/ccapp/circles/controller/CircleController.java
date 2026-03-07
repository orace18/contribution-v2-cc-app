package com.ccapp.circles.controller;

import com.ccapp.circles.dto.*;
import com.ccapp.circlesocial.dto.CircleActivityResponse;
import com.ccapp.circlesocial.dto.CircleProgressResponse;
import com.ccapp.circlesocial.dto.CircleScoreResponse;
import com.ccapp.circlesocial.service.CircleActivityService;
import com.ccapp.circlesocial.service.CircleProgressService;
import com.ccapp.circlesocial.service.CircleScoreService;
import com.ccapp.circles.service.CircleService;
import com.ccapp.common.api.CommonResponse;
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
    private final CircleActivityService circleActivityService;
    private final CircleProgressService circleProgressService;
    private final CircleScoreService circleScoreService;

    @PostMapping
    @Operation(summary = "Creer un cercle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes - cercle cree", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
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
