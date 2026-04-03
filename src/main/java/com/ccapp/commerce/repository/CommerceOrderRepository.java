package com.ccapp.commerce.repository;

import com.ccapp.commerce.domain.entity.CommerceOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommerceOrderRepository extends JpaRepository<CommerceOrder, UUID> {

    List<CommerceOrder> findByOrderIdIn(Collection<UUID> orderIds);

    List<CommerceOrder> findByBuyerAccountHolderIdOrderByCreatedAtDesc(UUID buyerAccountHolderId);

    List<CommerceOrder> findByMerchantAccountHolderIdOrderByCreatedAtDesc(UUID merchantAccountHolderId);
}
