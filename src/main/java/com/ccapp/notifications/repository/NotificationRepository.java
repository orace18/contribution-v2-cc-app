package com.ccapp.notifications.repository;

import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);

    List<Notification> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId, Pageable pageable);

    long countByAccountHolderId(UUID accountHolderId);

    long countByAccountHolderIdAndReadAtIsNull(UUID accountHolderId);

    boolean existsByAccountHolderIdAndTypeAndTemplateCodeAndCreatedAtBetween(
            UUID accountHolderId,
            NotificationType type,
            String templateCode,
            OffsetDateTime from,
            OffsetDateTime to
    );

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.id = :id AND n.accountHolderId = :accountHolderId AND n.readAt IS NULL")
    int markOneRead(@Param("id") UUID id, @Param("accountHolderId") UUID accountHolderId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.accountHolderId = :accountHolderId AND n.readAt IS NULL")
    int markAllRead(@Param("accountHolderId") UUID accountHolderId, @Param("now") OffsetDateTime now);
}
