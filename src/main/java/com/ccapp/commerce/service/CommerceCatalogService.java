package com.ccapp.commerce.service;

import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.dto.CommerceCreateProductRequest;
import com.ccapp.commerce.dto.CommerceProductResponse;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.dto.CreateShopProductRequest;
import com.ccapp.shop.dto.ShopProductResponse;
import com.ccapp.shop.repository.ShopProductRepository;
import com.ccapp.shop.service.ShopProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceCatalogService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final ShopProductService shopProductService;
    private final ShopProductRepository shopProductRepository;
    private final CommerceProductRepository commerceProductRepository;
    private final CommerceMerchantService commerceMerchantService;

    @Transactional(readOnly = true)
    public List<CommerceProductResponse> listActiveProducts() {
        return mapProducts(shopProductRepository.findByActiveTrueOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public List<CommerceProductResponse> listMerchantProducts(UUID merchantAccountHolderId) {
        return mapProducts(shopProductRepository.findByMerchantIdOrderByNameAsc(merchantAccountHolderId));
    }

    @Transactional(readOnly = true)
    public CommerceProductResponse getProduct(UUID productId) {
        ShopProduct product = shopProductService.findProduct(productId);
        return mapProducts(List.of(product)).stream().findFirst().orElseThrow();
    }

    @Transactional
    public CommerceProductResponse createProduct(UUID merchantAccountHolderId, CommerceCreateProductRequest request) {
        commerceMerchantService.ensureMerchant(merchantAccountHolderId);
        ShopProductResponse shopProduct = shopProductService.createProduct(
                merchantAccountHolderId,
                new CreateShopProductRequest(
                        request.name(),
                        request.description(),
                        request.price(),
                        request.currency(),
                        request.commissionPercent(),
                        request.cvValue(),
                        request.active(),
                        null,
                        null
                )
        );
        commerceProductRepository.save(CommerceProduct.builder()
                .productId(shopProduct.id())
                .dsbAmount(scale(request.dsbAmount()))
                .build());
        return getProduct(shopProduct.id());
    }

    private List<CommerceProductResponse> mapProducts(List<ShopProduct> products) {
        List<UUID> productIds = products.stream().map(ShopProduct::getId).toList();
        Map<UUID, CommerceProduct> commerceByProductId = new HashMap<>();
        commerceProductRepository.findByProductIdIn(productIds)
                .forEach(product -> commerceByProductId.put(product.getProductId(), product));

        Map<UUID, String> merchantNames = commerceMerchantService.resolveDisplayNames(
                products.stream().map(ShopProduct::getMerchantId).distinct().toList()
        );

        return products.stream()
                .map(product -> {
                    CommerceProduct commerceProduct = commerceByProductId.get(product.getId());
                    return new CommerceProductResponse(
                            product.getId(),
                            product.getMerchantId(),
                            merchantNames.get(product.getMerchantId()),
                            product.getName(),
                            product.getDescription(),
                            scale(product.getPrice()),
                            product.getCurrency(),
                            scale(product.getCvValue()),
                            product.getCommissionPercent() == null
                                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                                    : product.getCommissionPercent().setScale(2, RoundingMode.HALF_UP),
                            commerceProduct == null ? ZERO : scale(commerceProduct.getDsbAmount()),
                            product.isActive(),
                            product.getImageUrl()
                    );
                })
                .toList();
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }
}
