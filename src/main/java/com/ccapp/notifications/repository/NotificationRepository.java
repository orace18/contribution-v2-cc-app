package com.ccapp.notifications.repository;

import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);

    List<Notification> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId, Pageable pageable);

    long countByAccountHolderId(UUID accountHolderId);

    boolean existsByAccountHolderIdAndTypeAndTemplateCodeAndCreatedAtBetween(
            UUID accountHolderId,
            NotificationType type,
            String templateCode,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
