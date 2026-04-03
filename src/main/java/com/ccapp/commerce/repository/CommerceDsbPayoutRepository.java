package com.ccapp.commerce.repository;

import com.ccapp.commerce.domain.entity.CommerceDsbPayout;
import com.ccapp.commerce.domain.enums.CommerceDsbBeneficiaryRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommerceDsbPayoutRepository extends JpaRepository<CommerceDsbPayout, UUID> {

    List<CommerceDsbPayout> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<CommerceDsbPayout> findByOrderIdIn(Collection<UUID> orderIds);

    Optional<CommerceDsbPayout> findByOrderIdAndRole(UUID orderId, CommerceDsbBeneficiaryRole role);
}
