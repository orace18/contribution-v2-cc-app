package com.ccapp.shop.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.shop.dto.CreateShopProductRequest;
import com.ccapp.shop.dto.ShopProductResponse;
import com.ccapp.shop.service.LegacyShopMutationSecurityService;
import com.ccapp.shop.service.ShopProductService;
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
@RequestMapping("/shop/products")
@RequiredArgsConstructor
@Tag(name = "Shop Products", description = "Marketplace products")
@SecurityRequirement(name = "bearerAuth")
public class ShopProductController {

    private final ShopProductService shopProductService;
    private final LegacyShopMutationSecurityService legacyShopMutationSecurityService;

    @GetMapping
    @Operation(summary = "List active marketplace products")
    public ResponseEntity<CommonResponse<List<ShopProductResponse>>> list() {
        return ResponseEntity.ok(CommonResponse.success("Shop products", shopProductService.listActiveProducts()));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get product details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<ShopProductResponse>> get(@PathVariable UUID productId) {
        return ResponseEntity.ok(CommonResponse.success("Shop product", shopProductService.getProduct(productId)));
    }

    @PostMapping
    @Operation(summary = "Create marketplace product for current merchant")
    public ResponseEntity<CommonResponse<ShopProductResponse>> create(@Valid @RequestBody CreateShopProductRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Shop product created",
                shopProductService.createProduct(legacyShopMutationSecurityService.authorizeProductCreate(), request)
        ));
    }
}
