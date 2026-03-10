package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.identity.dto.OnboardingCatalogItemResponse;
import com.ccapp.identity.service.OnboardingCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Tag(name = "Public Onboarding Catalogs", description = "Onboarding public option catalogs")
@Slf4j
public class PublicOnboardingCatalogController {

    private final OnboardingCatalogService onboardingCatalogService;

    @GetMapping("/activity-sectors")
    @Operation(summary = "List onboarding activity sectors")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<OnboardingCatalogItemResponse>>> listActivitySectors() {
        log.debug("PublicOnboardingCatalogController listActivitySectors");
        return ResponseEntity.ok(CommonResponse.success("Activity sectors", onboardingCatalogService.listActivitySectors()));
    }

    @GetMapping("/income-brackets")
    @Operation(summary = "List onboarding income brackets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<OnboardingCatalogItemResponse>>> listIncomeBrackets() {
        log.debug("PublicOnboardingCatalogController listIncomeBrackets");
        return ResponseEntity.ok(CommonResponse.success("Income brackets", onboardingCatalogService.listIncomeBrackets()));
    }
}
