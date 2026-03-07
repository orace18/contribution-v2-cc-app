package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminCircleDetailsResponse;
import com.ccapp.admin.dto.AdminCircleSummaryResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminCircleManagementService;
import com.ccapp.common.api.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/circles")
@RequiredArgsConstructor
public class AdminCircleController {

    private final AdminCircleManagementService adminCircleManagementService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<AdminCircleSummaryResponse>>> listCircles() {
        List<AdminCircleSummaryResponse> response = adminCircleManagementService.listCircles();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_CIRCLES", "CIRCLE", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Circles", response));
    }

    @GetMapping("/{circleId}")
    public ResponseEntity<CommonResponse<AdminCircleDetailsResponse>> getCircle(@PathVariable UUID circleId) {
        AdminCircleDetailsResponse response = adminCircleManagementService.getCircle(circleId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_CIRCLE", "CIRCLE", circleId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Circle details", response));
    }

    @PostMapping("/{circleId}/suspend")
    public ResponseEntity<CommonResponse<AdminCircleSummaryResponse>> suspend(@PathVariable UUID circleId) {
        AdminCircleSummaryResponse response = adminCircleManagementService.suspendCircle(circleId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "SUSPEND_CIRCLE", "CIRCLE", circleId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Circle suspended", response));
    }
}
