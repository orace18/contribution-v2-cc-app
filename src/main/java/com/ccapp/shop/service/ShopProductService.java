package com.ccapp.shop.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.dto.CreateShopProductRequest;
import com.ccapp.shop.dto.ShopProductResponse;
import com.ccapp.shop.repository.ShopProductRepository;
import com.ccapp.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopProductService {

    private final ShopProductRepository shopProductRepository;
    private final AccountHolderService accountHolderService;

    @Transactional(readOnly = true)
    public List<ShopProductResponse> listActiveProducts() {
        return shopProductRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShopProductResponse getProduct(UUID productId) {
        return toResponse(findProduct(productId));
    }

    @Transactional
    public ShopProductResponse createProduct(UUID merchantId, CreateShopProductRequest request) {
        accountHolderService.getById(merchantId);
        ShopProduct saved = shopProductRepository.save(ShopProduct.builder()
                .merchantId(merchantId)
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .price(request.price())
                .currency(request.currency().trim().toUpperCase(Locale.ROOT))
                .commissionPercent(request.commissionPercent())
                .cvValue(request.cvValue())
                .active(request.active() == null || request.active())
                .category(request.category() != null ? request.category().trim() : null)
                .imageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null)
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ShopProduct findProduct(UUID productId) {
        return shopProductRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private ShopProductResponse toResponse(ShopProduct product) {
        return new ShopProductResponse(
                product.getId(),
                product.getMerchantId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getCurrency(),
                product.getCommissionPercent(),
                product.getCvValue(),
                product.isActive(),
                product.getImageUrl()
        );
    }
}
