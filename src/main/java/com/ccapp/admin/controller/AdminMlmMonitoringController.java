package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminCommissionPayoutBatchResponse;
import com.ccapp.admin.dto.AdminCommissionResponse;
import com.ccapp.admin.dto.AdminMlmNetworkHealthResponse;
import com.ccapp.admin.dto.AdminWeeklyVolumeResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminMlmMonitoringService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMlmMonitoringController {

    private final AdminMlmMonitoringService adminMlmMonitoringService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/mlm/network-health")
    public ResponseEntity<CommonResponse<AdminMlmNetworkHealthResponse>> networkHealth() {
        AdminMlmNetworkHealthResponse response = adminMlmMonitoringService.getNetworkHealth();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_MLM_NETWORK_HEALTH", "MLM", null, response);
        return ResponseEntity.ok(CommonResponse.success("MLM network health", response));
    }

    @GetMapping("/commissions")
    public ResponseEntity<CommonResponse<List<AdminCommissionResponse>>> commissions(@RequestParam(required = false) Integer weekId,
                                                                                      @RequestParam(required = false) CommissionType commissionType,
                                                                                      @RequestParam(required = false) CommissionStatus status,
                                                                                      @RequestParam(defaultValue = "0") int page,
                                                                                      @RequestParam(defaultValue = "20") int size) {
        List<AdminCommissionResponse> response = adminMlmMonitoringService.listCommissions(weekId, commissionType, status, page, size);
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "LIST_COMMISSIONS",
                "MLM_COMMISSION",
                null,
                "count=" + response.size() + ",weekId=" + weekId + ",commissionType=" + commissionType + ",status=" + status
        );
        return ResponseEntity.ok(CommonResponse.success("Commissions", response));
    }

    @GetMapping("/commission-payouts")
    public ResponseEntity<CommonResponse<List<AdminCommissionPayoutBatchResponse>>> commissionPayouts(@RequestParam(defaultValue = "0") int page,
                                                                                                        @RequestParam(defaultValue = "20") int size) {
        List<AdminCommissionPayoutBatchResponse> response = adminMlmMonitoringService.listCommissionPayouts(page, size);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_COMMISSION_PAYOUTS", "COMMISSION_PAYOUT", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Commission payouts", response));
    }

    @GetMapping("/weekly-volume")
    public ResponseEntity<CommonResponse<List<AdminWeeklyVolumeResponse>>> weeklyVolume(@RequestParam(defaultValue = "12") int limit) {
        List<AdminWeeklyVolumeResponse> response = adminMlmMonitoringService.listWeeklyVolume(limit);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_WEEKLY_VOLUME", "MLM_VOLUME_LEDGER", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Weekly volume", response));
    }
}
