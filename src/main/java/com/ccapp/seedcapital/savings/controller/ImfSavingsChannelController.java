package com.ccapp.seedcapital.savings.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.seedcapital.savings.dto.ConfigureImfSavingsChannelRequest;
import com.ccapp.seedcapital.savings.dto.ImfSavingsChannelResponse;
import com.ccapp.seedcapital.savings.service.ImfSavingsChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/me/imf-savings-channel")
@RequiredArgsConstructor
@Validated
@Tag(name = "IMF Savings Channel", description = "Canal personnel d'epargne IMF")
@SecurityRequirement(name = "bearerAuth")
public class ImfSavingsChannelController {

    private final ImfSavingsChannelService imfSavingsChannelService;

    @GetMapping
    @Operation(summary = "Consulter le canal personnel d'epargne IMF")
    public ResponseEntity<CommonResponse<ImfSavingsChannelResponse>> getMyChannel() {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "IMF savings channel",
                imfSavingsChannelService.getMyChannel(requesterAccountHolderId)
        ));
    }

    @PutMapping
    @Operation(summary = "Configurer ou mettre a jour le canal personnel d'epargne IMF")
    public ResponseEntity<CommonResponse<ImfSavingsChannelResponse>> configureMyChannel(
            @Valid @RequestBody ConfigureImfSavingsChannelRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "IMF savings channel updated",
                imfSavingsChannelService.upsertMyChannel(requesterAccountHolderId, request)
        ));
    }
}
