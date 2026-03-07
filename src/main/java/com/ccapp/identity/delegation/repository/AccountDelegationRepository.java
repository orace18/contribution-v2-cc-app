package com.ccapp.identity.delegation.repository;

import com.ccapp.identity.delegation.domain.entity.AccountDelegation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountDelegationRepository extends JpaRepository<AccountDelegation, UUID> {

    List<AccountDelegation> findByDelegateUserIdAndExpiresAtAfter(UUID delegateUserId, OffsetDateTime now);

    List<AccountDelegation> findByDelegateUserIdAndExpiresAtIsNull(UUID delegateUserId);

    Optional<AccountDelegation> findFirstByAccountHolderIdAndDelegateUserIdOrderByCreatedAtDesc(UUID accountHolderId, UUID delegateUserId);
}
