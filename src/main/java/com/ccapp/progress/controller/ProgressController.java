package com.ccapp.progress.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.progress.dto.UserProgressResponse;
import com.ccapp.progress.service.ProgressService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/progress")
@RequiredArgsConstructor
@Tag(name = "Progress", description = "Suivi de progression utilisateur")
@SecurityRequirement(name = "bearerAuth")
public class ProgressController {

    private final ProgressService progressService;

    @GetMapping("/me")
    @Operation(summary = "Recuperer la progression de l'utilisateur courant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<UserProgressResponse>> me(@RequestParam(required = false) UUID accountHolderId) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : actorAccountHolderId;
        UUID targetAccountHolderId = accountHolderId != null ? accountHolderId : actorAccountHolderId;

        return ResponseEntity.ok(CommonResponse.success(
                "User progress",
                progressService.getUserProgress(actorUserId, targetAccountHolderId)
        ));
    }
}
