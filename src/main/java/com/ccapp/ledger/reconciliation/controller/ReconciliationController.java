package com.ccapp.ledger.reconciliation.controller;

import com.ccapp.ledger.reconciliation.dto.ReconciliationImportRequest;
import com.ccapp.ledger.reconciliation.dto.ReconciliationImportResponse;
import com.ccapp.ledger.reconciliation.dto.SettlementSummaryItem;
import com.ccapp.ledger.reconciliation.service.ReconciliationService;
import com.ccapp.common.api.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reconciliation", description = "Import et synthese de rapprochement comptable")
@SecurityRequirement(name = "bearerAuth")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/import")
    @Operation(summary = "Importer un lot de rapprochement")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalide", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ReconciliationImportResponse>> importBatch(
            @Valid @RequestBody ReconciliationImportRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Reconciliation batch imported", reconciliationService.importBatch(request)));
    }

    @GetMapping("/settlement-summary")
    @Operation(summary = "Obtenir la synthese de settlement par devise")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Succes", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifie", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<SettlementSummaryItem>>> settlementSummary(@RequestParam String currency) {
        return ResponseEntity.ok(CommonResponse.success("Settlement summary", reconciliationService.settlementSummary(currency)));
    }
}
