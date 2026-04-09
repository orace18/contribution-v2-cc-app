package com.ccapp.financial.access.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.financial.access.dto.MemberFinancialProfileResponse;
import com.ccapp.financial.access.service.MemberFinancialProfileQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Tag(name = "Member Financial Profile", description = "Projection financiere canonique du membre")
@SecurityRequirement(name = "bearerAuth")
public class MemberFinancialProfileController {

    private final MemberFinancialProfileQueryService memberFinancialProfileQueryService;

    @GetMapping("/financial-profile")
    @Operation(summary = "Consulter mon profil financier canonique")
    public ResponseEntity<CommonResponse<MemberFinancialProfileResponse>> getMyFinancialProfile() {
        return ResponseEntity.ok(CommonResponse.success(
                "Financial profile",
                memberFinancialProfileQueryService.getMyProfile(SecurityUtils.currentAccountHolderId())
        ));
    }
}
