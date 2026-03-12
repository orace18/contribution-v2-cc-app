package com.ccapp.shop.repository;

import com.ccapp.shop.domain.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, UUID> {

    List<ShopOrder> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    @Query("""
            select count(o)
            from ShopOrder o
            where o.status = com.ccapp.shop.domain.enums.ShopOrderStatus.DELIVERED
              and not exists (
                    select l.id
                    from MlmVolumeLedger l
                    where l.sourceOrderId = o.id
              )
            """)
    long countDeliveredOrdersWithoutVolumeLedger();
}
