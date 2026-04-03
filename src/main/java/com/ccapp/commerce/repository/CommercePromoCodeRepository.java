package com.ccapp.commerce.repository;

import com.ccapp.commerce.domain.entity.CommercePromoCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommercePromoCodeRepository extends JpaRepository<CommercePromoCode, UUID> {

    Optional<CommercePromoCode> findByCodeIgnoreCase(String code);

    List<CommercePromoCode> findByOwnerAccountHolderIdOrderByCreatedAtDesc(UUID ownerAccountHolderId);
}
