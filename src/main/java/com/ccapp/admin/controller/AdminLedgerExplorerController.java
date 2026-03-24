package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminLedgerEntryResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminLedgerExplorerService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.payments.operation.domain.enums.OperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/admin/finance/ledger", "/admin/finance/ledger"})
@RequiredArgsConstructor
public class AdminLedgerExplorerController {

    private final AdminLedgerExplorerService adminLedgerExplorerService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<AdminLedgerEntryResponse>>> search(
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false) OperationType operationType,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResponse<AdminLedgerEntryResponse> response = adminLedgerExplorerService.search(
                accountCode,
                operationType,
                fromDate,
                toDate,
                page,
                size
        );
        adminAuditService.logAction(
                AdminSecurityUtils.currentAdminUserId(),
                "SEARCH_LEDGER_EXPLORER",
                "LEDGER_ENTRY",
                null,
                "filters(accountCode=" + accountCode + ",operationType=" + operationType + ",fromDate=" + fromDate + ",toDate=" + toDate + ")"
        );
        return ResponseEntity.ok(CommonResponse.success("Ledger entries", response));
    }
}
