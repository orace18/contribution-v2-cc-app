package com.ccapp.financing.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.dto.CreateFinancingOfferRequest;
import com.ccapp.financing.dto.FinancingOfferResponse;
import com.ccapp.financing.dto.OfferCountryConfigResponse;
import com.ccapp.financing.dto.OfferCountryConfigUpdateRequest;
import com.ccapp.financing.dto.UpdateFinancingOfferRequest;
import com.ccapp.financing.service.FinancingOfferAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/offers", "/admin/offers"})
@RequiredArgsConstructor
public class AdminFinancingOfferController {

    private final FinancingOfferAdminService financingOfferAdminService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<FinancingOfferResponse>>> listOffers(
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) FinancingOfferStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResponse<FinancingOfferResponse> response = financingOfferAdminService.listOffers(
                partnerId,
                status,
                search,
                page,
                size
        );
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "LIST_FINANCING_OFFERS", "FINANCING_OFFER", null,
                "filters(partnerId=" + partnerId + ",status=" + status + ")");
        return ResponseEntity.ok(CommonResponse.success("Financing offers", response));
    }

    @PostMapping
    public ResponseEntity<CommonResponse<FinancingOfferResponse>> createOffer(@Valid @RequestBody CreateFinancingOfferRequest request) {
        FinancingOfferResponse response = financingOfferAdminService.createOffer(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CREATE_FINANCING_OFFER", "FINANCING_OFFER", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Financing offer created", response));
    }

    @PutMapping("/{offerId}")
    public ResponseEntity<CommonResponse<FinancingOfferResponse>> updateOffer(@PathVariable UUID offerId,
                                                                               @Valid @RequestBody UpdateFinancingOfferRequest request) {
        FinancingOfferResponse response = financingOfferAdminService.updateOffer(offerId, request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "UPDATE_FINANCING_OFFER", "FINANCING_OFFER", offerId.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Financing offer updated", response));
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<CommonResponse<FinancingOfferResponse>> getOffer(@PathVariable UUID offerId) {
        FinancingOfferResponse response = financingOfferAdminService.getOffer(offerId);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "VIEW_FINANCING_OFFER", "FINANCING_OFFER", offerId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Financing offer", response));
    }

    @PutMapping("/{offerId}/countries")
    public ResponseEntity<CommonResponse<List<OfferCountryConfigResponse>>> updateOfferCountries(
            @PathVariable UUID offerId,
            @Valid @RequestBody OfferCountryConfigUpdateRequest request) {
        List<OfferCountryConfigResponse> response = financingOfferAdminService.updateOfferCountries(offerId, request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "UPDATE_OFFER_COUNTRY_CONFIG", "FINANCING_OFFER", offerId.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Offer country configuration updated", response));
    }
}
