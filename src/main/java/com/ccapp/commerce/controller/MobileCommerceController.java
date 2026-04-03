package com.ccapp.commerce.controller;

import com.ccapp.common.api.ApiEnvelope;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.commerce.dto.CommerceCheckoutRequest;
import com.ccapp.commerce.dto.CommerceOrderResponse;
import com.ccapp.commerce.dto.CommerceOrderSettlementResponse;
import com.ccapp.commerce.dto.CommercePayOrderRequest;
import com.ccapp.commerce.dto.CommerceProductResponse;
import com.ccapp.commerce.dto.CommerceValidatePromoCodeRequest;
import com.ccapp.commerce.dto.CommerceValidatePromoCodeResponse;
import com.ccapp.commerce.service.CommerceCatalogService;
import com.ccapp.commerce.service.CommerceOrderService;
import com.ccapp.commerce.service.CommercePromoCodeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/commerce")
@RequiredArgsConstructor
@Tag(name = "Mobile Commerce", description = "Mobile commerce/store endpoints")
@SecurityRequirement(name = "bearerAuth")
public class MobileCommerceController {

    private final CommerceCatalogService commerceCatalogService;
    private final CommercePromoCodeService commercePromoCodeService;
    private final CommerceOrderService commerceOrderService;

    @GetMapping("/products")
    @Operation(summary = "List active commerce products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = ApiEnvelope.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiEnvelope.class)))
    })
    public ResponseEntity<ApiEnvelope<List<CommerceProductResponse>>> products() {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Commerce products",
                commerceCatalogService.listActiveProducts()
        ));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get commerce product details")
    public ResponseEntity<ApiEnvelope<CommerceProductResponse>> product(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Commerce product",
                commerceCatalogService.getProduct(productId)
        ));
    }

    @PostMapping("/promo-codes/validate")
    @Operation(summary = "Validate a promo code for commerce checkout")
    public ResponseEntity<ApiEnvelope<CommerceValidatePromoCodeResponse>> validatePromoCode(
            @Valid @RequestBody CommerceValidatePromoCodeRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Promo code validated",
                commercePromoCodeService.validate(request)
        ));
    }

    @PostMapping("/orders/checkout")
    @Operation(summary = "Create a commerce order")
    public ResponseEntity<ApiEnvelope<CommerceOrderResponse>> checkout(
            @Valid @RequestBody CommerceCheckoutRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Commerce order created",
                commerceOrderService.checkout(SecurityUtils.currentAccountHolderId(), request)
        ));
    }

    @PostMapping("/orders/{orderId}/pay")
    @Operation(summary = "Pay a commerce order")
    public ResponseEntity<ApiEnvelope<CommerceOrderResponse>> pay(@PathVariable UUID orderId,
                                                                  @Valid @RequestBody CommercePayOrderRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(ApiEnvelope.success(
                "Commerce order payment processed",
                commerceOrderService.pay(resolveActorUserId(), accountHolderId, orderId, request)
        ));
    }

    @GetMapping("/orders/mine")
    @Operation(summary = "List current buyer commerce orders")
    public ResponseEntity<ApiEnvelope<List<CommerceOrderResponse>>> mine() {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Commerce orders",
                commerceOrderService.listBuyerOrders(SecurityUtils.currentAccountHolderId())
        ));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get current buyer commerce order details")
    public ResponseEntity<ApiEnvelope<CommerceOrderResponse>> order(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Commerce order",
                commerceOrderService.getBuyerOrder(SecurityUtils.currentAccountHolderId(), orderId)
        ));
    }

    @GetMapping("/orders/{orderId}/settlement")
    @Operation(summary = "Get commerce settlement details for the current buyer")
    public ResponseEntity<ApiEnvelope<CommerceOrderSettlementResponse>> settlement(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiEnvelope.success(
                "Commerce settlement",
                commerceOrderService.getBuyerSettlement(SecurityUtils.currentAccountHolderId(), orderId)
        ));
    }

    private UUID resolveActorUserId() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        return principal.userId() != null ? principal.userId() : SecurityUtils.currentAccountHolderId();
    }
}
