package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminWalletMonitoringResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminWalletMonitoringService;
import com.ccapp.common.api.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/admin/finance/wallet-monitoring", "/admin/finance/wallet-monitoring"})
@RequiredArgsConstructor
public class AdminWalletMonitoringController {

    private final AdminWalletMonitoringService adminWalletMonitoringService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<AdminWalletMonitoringResponse>> searchWallet(@RequestParam String phone) {
        AdminWalletMonitoringResponse response = adminWalletMonitoringService.searchByPhone(phone);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "SEARCH_WALLET_MONITORING", "WALLET", response.walletId().toString(), phone);
        return ResponseEntity.ok(CommonResponse.success("Wallet monitoring", response));
    }
}
