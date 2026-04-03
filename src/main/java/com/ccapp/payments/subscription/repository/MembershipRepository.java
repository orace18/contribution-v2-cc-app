package com.ccapp.payments.subscription.repository;

import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByOperationId(UUID operationId);

    Optional<Membership> findFirstByAccountHolderIdAndStatusOrderByActivatedAtDesc(UUID accountHolderId,
                                                                                    MembershipStatus status);

    Optional<Membership> findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(UUID accountHolderId,
                                                                                                   MembershipStatus status,
                                                                                                   OffsetDateTime threshold);

    Optional<Membership> findFirstByAccountHolderIdAndExpiresAtIsNotNullOrderByExpiresAtDescCreatedAtDesc(UUID accountHolderId);

    Optional<Membership> findFirstByAccountHolderIdOrderByExpiresAtDescCreatedAtDesc(UUID accountHolderId);

    List<Membership> findByStatus(MembershipStatus status);

    List<Membership> findByAccountHolderIdInOrderByAccountHolderIdAscExpiresAtDescCreatedAtDesc(Collection<UUID> accountHolderIds);
}
