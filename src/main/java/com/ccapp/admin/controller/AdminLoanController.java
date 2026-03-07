package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminLoanSummaryResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminLoanManagementService;
import com.ccapp.common.api.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/loans")
@RequiredArgsConstructor
public class AdminLoanController {

    private final AdminLoanManagementService adminLoanManagementService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<AdminLoanSummaryResponse>>> listLoans() {
        List<AdminLoanSummaryResponse> response = adminLoanManagementService.listLoans();
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_LOANS", "LOAN", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Loans", response));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<CommonResponse<AdminLoanSummaryResponse>> getLoan(@PathVariable UUID loanId) {
        AdminLoanSummaryResponse response = adminLoanManagementService.getLoan(loanId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_LOAN", "LOAN", loanId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Loan details", response));
    }

    @PostMapping("/{loanId}/approve")
    public ResponseEntity<CommonResponse<AdminLoanSummaryResponse>> approve(@PathVariable UUID loanId) {
        AdminLoanSummaryResponse response = adminLoanManagementService.approveLoan(loanId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "APPROVE_LOAN", "LOAN", loanId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Loan approved", response));
    }

    @PostMapping("/{loanId}/reject")
    public ResponseEntity<CommonResponse<AdminLoanSummaryResponse>> reject(@PathVariable UUID loanId) {
        AdminLoanSummaryResponse response = adminLoanManagementService.rejectLoan(loanId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "REJECT_LOAN", "LOAN", loanId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Loan rejected", response));
    }
}
