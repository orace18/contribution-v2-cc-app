package com.ccapp.admin.controller;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.dto.AdminBackofficeMerchantRequest;
import com.ccapp.admin.dto.AdminBackofficeMerchantResponse;
import com.ccapp.admin.dto.AdminBackofficeOrderResponse;
import com.ccapp.admin.dto.AdminBackofficeProductRequest;
import com.ccapp.admin.dto.AdminBackofficeProductResponse;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.admin.service.AdminBackofficeCommerceService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/backoffice")
public class AdminBackofficeCommerceController {

    private final AdminBackofficeCommerceService adminBackofficeCommerceService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/merchants")
    public ResponseEntity<CommonResponse<List<AdminBackofficeMerchantResponse>>> listMerchants(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) CommerceMerchantStatus status) {
        List<AdminBackofficeMerchantResponse> response = adminBackofficeCommerceService.listMerchants(countryCode, status);
        return ResponseEntity.ok(CommonResponse.success("Backoffice merchants", response));
    }

    @PostMapping("/merchants")
    public ResponseEntity<CommonResponse<AdminBackofficeMerchantResponse>> createMerchant(
            @Valid @RequestBody AdminBackofficeMerchantRequest request) {
        AdminBackofficeMerchantResponse response = adminBackofficeCommerceService.createMerchant(request);
        adminAuditService.logAction(AdminSecurityUtils.currentAdminUserId(), "CREATE_BACKOFFICE_MERCHANT", "MERCHANT", response.merchantId().toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Backoffice merchant created", response));
    }

    @PatchMapping("/merchants/{merchantId}")
    public ResponseEntity<CommonResponse<AdminBackofficeMerchantResponse>> updateMerchant(
            @PathVariable UUID merchantId,
            @Valid @RequestBody AdminBackofficeMerchantRequest request) {
        AdminBackofficeMerchantResponse response = adminBackofficeCommerceService.updateMerchant(merchantId, request);
        return ResponseEntity.ok(CommonResponse.success("Backoffice merchant updated", response));
    }

    @PostMapping("/merchants/{merchantId}/activate")
    public ResponseEntity<CommonResponse<AdminBackofficeMerchantResponse>> activateMerchant(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice merchant activated",
                adminBackofficeCommerceService.updateMerchantStatus(merchantId, CommerceMerchantStatus.ACTIVE)
        ));
    }

    @PostMapping("/merchants/{merchantId}/deactivate")
    public ResponseEntity<CommonResponse<AdminBackofficeMerchantResponse>> deactivateMerchant(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice merchant deactivated",
                adminBackofficeCommerceService.updateMerchantStatus(merchantId, CommerceMerchantStatus.INACTIVE)
        ));
    }

    @GetMapping("/products")
    public ResponseEntity<CommonResponse<List<AdminBackofficeProductResponse>>> listProducts(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String status) {
        Boolean active = null;
        if (status != null && !status.isBlank()) {
            active = "ACTIVE".equalsIgnoreCase(status);
        }
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice products",
                adminBackofficeCommerceService.listProducts(countryCode, active)
        ));
    }

    @PostMapping("/products")
    public ResponseEntity<CommonResponse<AdminBackofficeProductResponse>> createProduct(
            @Valid @RequestBody AdminBackofficeProductRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice product created",
                adminBackofficeCommerceService.createProduct(request)
        ));
    }

    @PatchMapping("/products/{productId}")
    public ResponseEntity<CommonResponse<AdminBackofficeProductResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody AdminBackofficeProductRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice product updated",
                adminBackofficeCommerceService.updateProduct(productId, request)
        ));
    }

    @PostMapping("/products/{productId}/activate")
    public ResponseEntity<CommonResponse<AdminBackofficeProductResponse>> activateProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice product activated",
                adminBackofficeCommerceService.updateProductStatus(productId, true)
        ));
    }

    @PostMapping("/products/{productId}/deactivate")
    public ResponseEntity<CommonResponse<AdminBackofficeProductResponse>> deactivateProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice product deactivated",
                adminBackofficeCommerceService.updateProductStatus(productId, false)
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<CommonResponse<List<AdminBackofficeOrderResponse>>> listOrders(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) ShopOrderStatus status,
            @RequestParam(required = false) CommerceSettlementStatus settlementStatus) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice orders",
                adminBackofficeCommerceService.listOrders(countryCode, status, settlementStatus)
        ));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<CommonResponse<AdminBackofficeOrderResponse>> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice order",
                adminBackofficeCommerceService.getOrder(orderId)
        ));
    }

    @PostMapping("/orders/{orderId}/confirm-delivery")
    public ResponseEntity<CommonResponse<AdminBackofficeOrderResponse>> confirmOrderDelivery(@PathVariable UUID orderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice order delivered",
                adminBackofficeCommerceService.confirmOrderDelivery(orderId)
        ));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<CommonResponse<AdminBackofficeOrderResponse>> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice order cancelled",
                adminBackofficeCommerceService.cancelOrder(orderId)
        ));
    }

    @PostMapping("/orders/{orderId}/refund")
    public ResponseEntity<CommonResponse<AdminBackofficeOrderResponse>> refundOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Backoffice order refunded",
                adminBackofficeCommerceService.refundOrder(orderId)
        ));
    }
}
