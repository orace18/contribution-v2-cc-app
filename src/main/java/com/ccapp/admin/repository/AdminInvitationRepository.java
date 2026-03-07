package com.ccapp.admin.repository;

import com.ccapp.admin.domain.entity.AdminInvitation;
import com.ccapp.admin.domain.enums.AdminInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, UUID> {

    Optional<AdminInvitation> findByInvitationToken(String invitationToken);

    List<AdminInvitation> findAllByOrderByCreatedAtDesc();

    List<AdminInvitation> findByStatusAndExpiresAtBefore(AdminInvitationStatus status, OffsetDateTime threshold);
}
