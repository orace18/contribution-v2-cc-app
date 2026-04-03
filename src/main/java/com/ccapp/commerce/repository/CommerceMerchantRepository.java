package com.ccapp.commerce.repository;

import com.ccapp.commerce.domain.entity.CommerceMerchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommerceMerchantRepository extends JpaRepository<CommerceMerchant, UUID> {

    Optional<CommerceMerchant> findByAccountHolderId(UUID accountHolderId);

    List<CommerceMerchant> findByAccountHolderIdIn(Collection<UUID> accountHolderIds);
}
