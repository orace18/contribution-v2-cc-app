package com.ccapp.payments.subscription.repository;

import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByOperationId(UUID operationId);

    Optional<Membership> findFirstByAccountHolderIdAndStatusOrderByActivatedAtDesc(UUID accountHolderId,
                                                                                    MembershipStatus status);
}
