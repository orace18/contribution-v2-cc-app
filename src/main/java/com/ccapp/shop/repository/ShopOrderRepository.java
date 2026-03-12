package com.ccapp.shop.repository;

import com.ccapp.shop.domain.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, UUID> {

    List<ShopOrder> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);
}
