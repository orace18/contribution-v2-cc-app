package com.ccapp.mobile.auth.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.mobile.auth.dto.MobileSocialLoginRequest;
import com.ccapp.mobile.auth.dto.MobileSocialLoginResponse;
import com.ccapp.mobile.auth.service.MobileAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
@RequiredArgsConstructor
@Tag(name = "Mobile Auth", description = "Mobile authentication APIs")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    @PostMapping("/social-login")
    @Operation(summary = "Authenticate/signup mobile user from social provider token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<MobileSocialLoginResponse>> socialLogin(
            @Valid @RequestBody MobileSocialLoginRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Mobile social login success",
                mobileAuthService.socialLogin(request)
        ));
    }
}
