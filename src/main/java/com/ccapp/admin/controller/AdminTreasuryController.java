package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.*;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminTreasuryService;
import com.ccapp.common.api.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/treasury")
@RequiredArgsConstructor
public class AdminTreasuryController {

    private final AdminTreasuryService adminTreasuryService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/accounts")
    public ResponseEntity<CommonResponse<List<TreasuryAccountResponse>>> accounts(@RequestParam(required = false) String currency) {
        List<TreasuryAccountResponse> response = adminTreasuryService.getAccounts(currency);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_TREASURY_ACCOUNTS", "TREASURY", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Treasury accounts", response));
    }

    @PostMapping("/transfer")
    public ResponseEntity<CommonResponse<TreasuryOperationResponse>> transfer(@Valid @RequestBody TreasuryTransferRequest request) {
        TreasuryOperationResponse response = adminTreasuryService.transfer(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "TREASURY_TRANSFER", "TREASURY", response.operationId().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Treasury transfer completed", response));
    }

    @PostMapping("/cashout")
    public ResponseEntity<CommonResponse<TreasuryOperationResponse>> cashout(@Valid @RequestBody TreasuryCashoutRequest request) {
        TreasuryOperationResponse response = adminTreasuryService.cashout(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "TREASURY_CASHOUT", "TREASURY", response.operationId().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Treasury cashout completed", response));
    }
}
