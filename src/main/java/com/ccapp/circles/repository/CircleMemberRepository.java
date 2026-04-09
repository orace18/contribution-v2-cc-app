package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CircleMemberRepository extends JpaRepository<CircleMember, UUID> {

    boolean existsByCircle_IdAndAccountHolderId(UUID circleId, UUID accountHolderId);

    @Deprecated(since = "2026-04", forRemoval = false)
    boolean existsByCircle_IdAndAccountHolderIdAndFinancialMemberTrue(UUID circleId, UUID accountHolderId);

    Optional<CircleMember> findByCircle_IdAndAccountHolderId(UUID circleId, UUID accountHolderId);

    long countByCircle_Id(UUID circleId);

    @Deprecated(since = "2026-04", forRemoval = false)
    long countByCircle_IdAndFinancialMemberTrue(UUID circleId);

    List<CircleMember> findByCircle_IdOrderByJoinedAtAsc(UUID circleId);

    @Deprecated(since = "2026-04", forRemoval = false)
    List<CircleMember> findByCircle_IdAndFinancialMemberTrueOrderByJoinedAtAsc(UUID circleId);

    Optional<CircleMember> findByCircle_IdAndRole(UUID circleId, CircleMemberRole role);

    @Query("""
            select count(cm) from CircleMember cm, AccountHolder ah
            where cm.circle.id = :circleId
              and cm.accountHolderId = ah.id
              and ah.accountType = com.ccapp.identity.account_holder.domain.enums.AccountType.ASSISTED
            """)
    long countAssistedMembersByCircleId(UUID circleId);
}
