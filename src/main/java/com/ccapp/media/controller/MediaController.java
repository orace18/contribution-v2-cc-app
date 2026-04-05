package com.ccapp.media.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.media.domain.dto.*;
import com.ccapp.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Media upload and retrieval")
@SecurityRequirement(name = "bearerAuth")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload-url")
    @Operation(summary = "Create pre-signed upload URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CreateUploadUrlResponse>> createUploadUrl(
            @Valid @RequestBody CreateUploadUrlRequest request) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : actorAccountHolderId;
        return ResponseEntity.ok(CommonResponse.success(
                "Upload URL generated",
                mediaService.createUploadUrl(actorUserId, actorAccountHolderId, request)
        ));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm uploaded media file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<MediaFileResponse>> confirm(
            @Valid @RequestBody ConfirmUploadRequest request) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : actorAccountHolderId;
        return ResponseEntity.ok(CommonResponse.success(
                "Media upload confirmed",
                mediaService.confirmUpload(actorUserId, actorAccountHolderId, request)
        ));
    }

    @PostMapping(value = "/{id}/upload-proxy", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Upload media bytes directly via backend proxy (fallback when presigned URL is unreachable)")
    public ResponseEntity<CommonResponse<MediaFileResponse>> uploadProxy(
            @PathVariable UUID id,
            @RequestBody byte[] data,
            @RequestHeader("X-Media-Content-Type") String contentType) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : actorAccountHolderId;
        return ResponseEntity.ok(CommonResponse.success(
                "Media uploaded",
                mediaService.proxyUpload(actorUserId, actorAccountHolderId, id, data, contentType)
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get media metadata")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<MediaFileResponse>> getMedia(@PathVariable UUID id) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : actorAccountHolderId;
        return ResponseEntity.ok(CommonResponse.success(
                "Media file",
                mediaService.getMedia(actorUserId, actorAccountHolderId, id)
        ));
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "Create pre-signed download URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<DownloadUrlResponse>> createDownloadUrl(@PathVariable UUID id) {
        UUID actorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId() != null ? principal.userId() : actorAccountHolderId;
        return ResponseEntity.ok(CommonResponse.success(
                "Download URL generated",
                mediaService.createDownloadUrl(actorUserId, actorAccountHolderId, id)
        ));
    }
}
