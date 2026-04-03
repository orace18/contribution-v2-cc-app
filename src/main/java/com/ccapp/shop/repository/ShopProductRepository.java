package com.ccapp.shop.repository;

import com.ccapp.shop.domain.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShopProductRepository extends JpaRepository<ShopProduct, UUID> {

    List<ShopProduct> findByActiveTrueOrderByNameAsc();

    List<ShopProduct> findByMerchantIdOrderByNameAsc(UUID merchantId);
}
