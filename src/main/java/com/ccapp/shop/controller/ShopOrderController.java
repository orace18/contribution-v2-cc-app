package com.ccapp.shop.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.shop.dto.CheckoutRequest;
import com.ccapp.shop.dto.ShopOrderResponse;
import com.ccapp.shop.dto.UpdateShopOrderStatusRequest;
import com.ccapp.shop.service.ShopOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shop/orders")
@RequiredArgsConstructor
@Tag(name = "Shop Orders", description = "Marketplace order lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class ShopOrderController {

    private final ShopOrderService shopOrderService;

    @PostMapping("/checkout")
    @Operation(summary = "Checkout cart and create order")
    public ResponseEntity<CommonResponse<ShopOrderResponse>> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Shop order created",
                shopOrderService.checkout(SecurityUtils.currentAccountHolderId(), request)
        ));
    }

    @GetMapping("/mine")
    @Operation(summary = "List orders of current buyer")
    public ResponseEntity<CommonResponse<List<ShopOrderResponse>>> mine() {
        return ResponseEntity.ok(CommonResponse.success(
                "Shop buyer orders",
                shopOrderService.listBuyerOrders(SecurityUtils.currentAccountHolderId())
        ));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details for current buyer")
    public ResponseEntity<CommonResponse<ShopOrderResponse>> get(@PathVariable UUID orderId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Shop order",
                shopOrderService.getBuyerOrder(SecurityUtils.currentAccountHolderId(), orderId)
        ));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Update order status")
    public ResponseEntity<CommonResponse<ShopOrderResponse>> updateStatus(@PathVariable UUID orderId,
                                                                          @Valid @RequestBody UpdateShopOrderStatusRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Shop order status updated",
                shopOrderService.updateStatus(orderId, request.status())
        ));
    }
}
