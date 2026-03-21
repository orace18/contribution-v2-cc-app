package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleInvite;
import com.ccapp.circles.domain.enums.CircleInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CircleInviteRepository extends JpaRepository<CircleInvite, UUID> {

    Optional<CircleInvite> findByIdAndCircle_Id(UUID id, UUID circleId);

    Optional<CircleInvite> findByInvitationToken(String invitationToken);

    long countByCircle_IdAndStatus(UUID circleId, CircleInviteStatus status);

    long countByCreatedByAccountHolderId(UUID accountHolderId);
}
