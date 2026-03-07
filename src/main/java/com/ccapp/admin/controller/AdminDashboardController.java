package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminDashboardResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminDashboardService;
import com.ccapp.common.api.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<AdminDashboardResponse>> dashboard() {
        AdminDashboardResponse response = adminDashboardService.getDashboard();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_DASHBOARD", "DASHBOARD", null, null);
        return ResponseEntity.ok(CommonResponse.success("Admin dashboard", response));
    }
}
