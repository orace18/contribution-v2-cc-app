package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.identity.dto.BlockAccountRequest;
import com.ccapp.identity.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/identity")
@RequiredArgsConstructor
@Tag(name = "Internal Identity", description = "Operations internes sur les comptes utilisateurs")
@SecurityRequirement(name = "apiKeyAuth")
@Slf4j
public class InternalIdentityController {

    private final IdentityService identityService;

    @PostMapping("/users/{userId}/block")
    @Operation(summary = "Bloquer un compte utilisateur (interne)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<Void>> blockAccount(@PathVariable UUID userId,
                                                             @Valid @RequestBody BlockAccountRequest request) {
        log.info("InternalIdentityController blockAccount userId={} reason={}", userId, request.reason());
        identityService.blockAccount(userId, request.reason());
        return ResponseEntity.ok(CommonResponse.success("Account blocked"));
    }
}
