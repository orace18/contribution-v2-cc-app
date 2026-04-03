package com.ccapp.shop.repository;

import com.ccapp.shop.domain.entity.ShopOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShopOrderItemRepository extends JpaRepository<ShopOrderItem, UUID> {

    List<ShopOrderItem> findByOrderId(UUID orderId);

    List<ShopOrderItem> findByOrderIdIn(List<UUID> orderIds);
}
