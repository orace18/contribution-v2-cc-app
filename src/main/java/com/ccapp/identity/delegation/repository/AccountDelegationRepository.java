package com.ccapp.identity.delegation.repository;

import com.ccapp.identity.delegation.domain.entity.AccountDelegation;
import com.ccapp.identity.delegation.domain.enums.DelegationRole;
import com.ccapp.identity.delegation.domain.enums.DelegationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountDelegationRepository extends JpaRepository<AccountDelegation, UUID> {

    List<AccountDelegation> findByDelegateUserIdAndStatusAndExpiresAtAfter(UUID delegateUserId, DelegationStatus status, OffsetDateTime now);

    List<AccountDelegation> findByDelegateUserIdAndStatusAndExpiresAtIsNull(UUID delegateUserId, DelegationStatus status);

    Optional<AccountDelegation> findFirstByAccountHolderIdAndDelegateUserIdAndStatusOrderByCreatedAtDesc(
            UUID accountHolderId,
            UUID delegateUserId,
            DelegationStatus status
    );

    Optional<AccountDelegation> findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(UUID accountHolderId, DelegationStatus status);

    Optional<AccountDelegation> findFirstByAccountHolderIdAndDelegateUserIdAndRoleAndStatusOrderByCreatedAtDesc(
            UUID accountHolderId,
            UUID delegateUserId,
            DelegationRole role,
            DelegationStatus status
    );

    List<AccountDelegation> findByAccountHolderIdAndStatus(UUID accountHolderId, DelegationStatus status);
}
