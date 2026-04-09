package com.ccapp.credit.application.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.credit.application.dto.CreateSeedCapitalCreditApplicationRequest;
import com.ccapp.credit.application.dto.CreditApplicationResponse;
import com.ccapp.credit.application.service.CreditApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/credit-applications")
@RequiredArgsConstructor
@Tag(name = "Credit Applications", description = "Demandes canoniques de credit")
@SecurityRequirement(name = "bearerAuth")
public class CreditApplicationController {

    private final CreditApplicationService creditApplicationService;

    @PostMapping("/seed-capital")
    @Operation(summary = "Creer la demande canonique de seed capital")
    public ResponseEntity<CommonResponse<CreditApplicationResponse>> createSeedCapitalApplication(
            @Valid @RequestBody CreateSeedCapitalCreditApplicationRequest request) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Credit application submitted",
                creditApplicationService.createSeedCapitalApplication(
                        resolveActorUserId(),
                        requesterAccountHolderId,
                        request
                )
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Lister mes demandes canoniques de credit")
    public ResponseEntity<CommonResponse<List<CreditApplicationResponse>>> myApplications() {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "My credit applications",
                creditApplicationService.getMyApplications(resolveActorUserId(), requesterAccountHolderId)
        ));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Consulter une demande canonique de credit")
    public ResponseEntity<CommonResponse<CreditApplicationResponse>> getApplication(@PathVariable UUID applicationId) {
        UUID requesterAccountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success(
                "Credit application",
                creditApplicationService.getApplication(resolveActorUserId(), requesterAccountHolderId, applicationId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
