package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.identity.dto.LinkAssistedAccountRequest;
import com.ccapp.identity.dto.LinkAssistedAccountResponse;
import com.ccapp.identity.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/identity")
@RequiredArgsConstructor
@Tag(name = "Mobile Identity", description = "Mobile identity flows")
@SecurityRequirement(name = "bearerAuth")
public class MobileIdentityController {

    private final IdentityService identityService;

    @PostMapping("/link-account")
    @Operation(summary = "Link an assisted account to the authenticated smartphone user")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Success",
                    content = @Content(
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Account linked\",\"data\":{\"appUserId\":\"79e4fd8c-37a7-40f0-83a7-50f4ec02f59c\",\"accountHolderId\":\"8df991be-c9f2-45a5-9a2d-36dd6204aff3\",\"accountType\":\"STANDARD\",\"countryCode\":\"BJ\",\"serviceCountryCode\":\"BJ\",\"linkedAt\":\"2026-03-10T11:15:00Z\"},\"timestamp\":\"2026-03-10T11:15:00Z\"}")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assisted account not found", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Identity or mapping conflict", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<LinkAssistedAccountResponse>> linkAccount(
            @Valid @RequestBody LinkAssistedAccountRequest request) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        LinkAssistedAccountResponse response = identityService.linkAssistedAccount(
                principal.firebaseUid(),
                principal.email(),
                request
        );
        return ResponseEntity.ok(CommonResponse.success("Account linked", response));
    }
}
