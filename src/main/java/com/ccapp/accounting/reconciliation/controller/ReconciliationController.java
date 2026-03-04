package com.ccapp.accounting.reconciliation.controller;

import com.ccapp.accounting.reconciliation.dto.ReconciliationImportRequest;
import com.ccapp.accounting.reconciliation.dto.ReconciliationImportResponse;
import com.ccapp.accounting.reconciliation.dto.SettlementSummaryItem;
import com.ccapp.accounting.reconciliation.service.ReconciliationService;
import com.ccapp.common.api.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
@Validated
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/import")
    public ResponseEntity<CommonResponse<ReconciliationImportResponse>> importBatch(
            @Valid @RequestBody ReconciliationImportRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Reconciliation batch imported", reconciliationService.importBatch(request)));
    }

    @GetMapping("/settlement-summary")
    public ResponseEntity<CommonResponse<List<SettlementSummaryItem>>> settlementSummary(@RequestParam String currency) {
        return ResponseEntity.ok(CommonResponse.success("Settlement summary", reconciliationService.settlementSummary(currency)));
    }
}
