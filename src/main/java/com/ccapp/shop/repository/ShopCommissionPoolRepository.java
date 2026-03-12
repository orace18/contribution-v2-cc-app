package com.ccapp.shop.repository;

import com.ccapp.shop.domain.entity.ShopCommissionPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShopCommissionPoolRepository extends JpaRepository<ShopCommissionPool, UUID> {

    Optional<ShopCommissionPool> findByOrderId(UUID orderId);
}
