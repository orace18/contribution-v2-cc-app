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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/capital")
@RequiredArgsConstructor
@Slf4j
@Deprecated(since = "2026-04", forRemoval = false)
public class AdminCapitalController {

    private final FundService fundService;
    private final CapitalPoolService capitalPoolService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/funds")
    @Deprecated
    public ResponseEntity<CommonResponse<List<FundResponse>>> listFunds(@RequestParam(required = false) String currency) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        log.warn("legacy_endpoint_invoked endpointId={} adminUserId={} currencyFilterPresent={}",
                "admin-capital.list-funds",
                adminUserId,
                currency != null && !currency.isBlank());
        List<FundResponse> response = fundService.listFunds(currency);
        adminAuditService.logAction(adminUserId, "LIST_FUNDS", "FUND", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Funds", response));
    }

    @PostMapping("/funds")
    @Deprecated
    public ResponseEntity<CommonResponse<FundResponse>> createFund(@Valid @RequestBody CreateFundRequest request) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        log.warn("legacy_endpoint_invoked endpointId={} adminUserId={} currency={} provider={}",
                "admin-capital.create-fund",
                adminUserId,
                request.currency(),
                request.provider());
        FundResponse response = fundService.createFund(request);
        adminAuditService.logAction(adminUserId, "CREATE_FUND", "FUND", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Fund created", response));
    }

    @GetMapping("/pools")
    @Deprecated
    public ResponseEntity<CommonResponse<List<CapitalPoolResponse>>> listPools(@RequestParam(required = false) UUID circleId) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        log.warn("legacy_endpoint_invoked endpointId={} adminUserId={} circleId={}",
                "admin-capital.list-pools",
                adminUserId,
                circleId);
        List<CapitalPoolResponse> response = capitalPoolService.listPools(circleId);
        adminAuditService.logAction(adminUserId, "LIST_CAPITAL_POOLS", "CAPITAL_POOL", null, "count=" + response.size());
        return ResponseEntity.ok(CommonResponse.success("Capital pools", response));
    }

    @PostMapping("/pools/allocate")
    @Deprecated
    public ResponseEntity<CommonResponse<CapitalPoolResponse>> allocate(@Valid @RequestBody AllocateCapitalPoolRequest request) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        log.warn("legacy_endpoint_invoked endpointId={} adminUserId={} circleId={} fundId={} hasAmount={}",
                "admin-capital.allocate-pool",
                adminUserId,
                request.circleId(),
                request.fundId(),
                request.allocatedAmount() != null);
        CapitalPoolResponse response = capitalPoolService.allocatePool(request);
        adminAuditService.logAction(adminUserId, "ALLOCATE_CAPITAL_POOL", "CAPITAL_POOL", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Capital pool allocated", response));
    }
}
