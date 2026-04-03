package com.ccapp.commerce.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.commerce.dto.CommerceCreateProductRequest;
import com.ccapp.commerce.dto.CommerceCreatePromoCodeRequest;
import com.ccapp.commerce.dto.CommerceOrderResponse;
import com.ccapp.commerce.dto.CommerceOrderSettlementResponse;
import com.ccapp.commerce.dto.CommerceProductResponse;
import com.ccapp.commerce.dto.CommercePromoCodeResponse;
import com.ccapp.commerce.service.CommerceCatalogService;
import com.ccapp.commerce.service.CommerceOrderService;
import com.ccapp.commerce.service.CommercePromoCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant/commerce")
@RequiredArgsConstructor
@Tag(name = "Merchant Commerce", description = "Merchant commerce management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class MerchantCommerceController {

    private final CommerceCatalogService commerceCatalogService;
    private final CommercePromoCodeService commercePromoCodeService;
    private final CommerceOrderService commerceOrderService;

    @GetMapping("/products")
    @Operation(summary = "List products of the current merchant")
    public ResponseEntity<CommonResponse<List<CommerceProductResponse>>> products() {
        return ResponseEntity.ok(CommonResponse.success(
                "Merchant commerce products",
                commerceCatalogService.listMerchantProducts(SecurityUtils.currentAccountHolderId())
        ));
    }

    @PostMapping("/products")
    @Operation(summary = "Create a commerce product for the current merchant")
    public ResponseEntity<CommonResponse<CommerceProductResponse>> createProduct(
            @Valid @RequestBody CommerceCreateProductRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Commerce product created",
                commerceCatalogService.createProduct(SecurityUtils.currentAccountHolderId(), request)
        ));
    }

    @GetMapping("/promo-codes")
    @Operation(summary = "List promo codes of the current merchant")
    public ResponseEntity<CommonResponse<List<CommercePromoCodeResponse>>> promoCodes() {
        return ResponseEntity.ok(CommonResponse.success(
                "Merchant commerce promo codes",
                commercePromoCodeService.listByOwner(SecurityUtils.currentAccountHolderId())
        ));
    }

    @PostMapping("/promo-codes")
    @Operation(summary = "Create a promo code for the current merchant")
    public ResponseEntity<CommonResponse<CommercePromoCodeResponse>> createPromoCode(
            @Valid @RequestBody CommerceCreatePromoCodeRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Commerce promo code created",
                commercePromoCodeService.create(SecurityUtils.currentAccountHolderId(), request)
        ));
    }

    @PostMapping("/orders/{orderId}/confirm-delivery")
    @Operation(summary = "Confirm delivery of a commerce order")
    public ResponseEntity<CommonResponse<CommerceOrderResponse>> confirmDelivery(@PathVariable UUID orderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Commerce order delivered",
                commerceOrderService.confirmDelivery(resolveActorUserId(), SecurityUtils.currentAccountHolderId(), orderId)
        ));
    }

    @GetMapping("/settlements")
    @Operation(summary = "List settlement history of the current merchant")
    public ResponseEntity<CommonResponse<List<CommerceOrderSettlementResponse>>> settlements() {
        return ResponseEntity.ok(CommonResponse.success(
                "Merchant commerce settlements",
                commerceOrderService.listMerchantSettlements(SecurityUtils.currentAccountHolderId())
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
