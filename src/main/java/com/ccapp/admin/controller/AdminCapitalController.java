package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.capital.dto.AllocateCapitalPoolRequest;
import com.ccapp.capital.dto.CapitalPoolResponse;
import com.ccapp.capital.dto.CreateFundRequest;
import com.ccapp.capital.dto.FundResponse;
import com.ccapp.capital.service.CapitalPoolService;
import com.ccapp.capital.service.FundService;
import com.ccapp.common.api.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/capital")
@RequiredArgsConstructor
public class AdminCapitalController {

    private final FundService fundService;
    private final CapitalPoolService capitalPoolService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/funds")
    public ResponseEntity<CommonResponse<List<FundResponse>>> listFunds(@RequestParam(required = false) String currency) {
        List<FundResponse> response = fundService.listFunds(currency);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_FUNDS", "FUND", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Funds", response));
    }

    @PostMapping("/funds")
    public ResponseEntity<CommonResponse<FundResponse>> createFund(@Valid @RequestBody CreateFundRequest request) {
        FundResponse response = fundService.createFund(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CREATE_FUND", "FUND", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Fund created", response));
    }

    @GetMapping("/pools")
    public ResponseEntity<CommonResponse<List<CapitalPoolResponse>>> listPools(@RequestParam(required = false) UUID circleId) {
        List<CapitalPoolResponse> response = capitalPoolService.listPools(circleId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_CAPITAL_POOLS", "CAPITAL_POOL", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Capital pools", response));
    }

    @PostMapping("/pools/allocate")
    public ResponseEntity<CommonResponse<CapitalPoolResponse>> allocate(@Valid @RequestBody AllocateCapitalPoolRequest request) {
        CapitalPoolResponse response = capitalPoolService.allocatePool(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "ALLOCATE_CAPITAL_POOL", "CAPITAL_POOL", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Capital pool allocated", response));
    }
}
