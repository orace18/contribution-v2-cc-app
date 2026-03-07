package com.ccapp.notifications.repository;

import com.ccapp.notifications.domain.entity.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, UUID> {

    int countByNotification_Id(UUID notificationId);

    List<NotificationAttempt> findByNotification_IdOrderByCreatedAtDesc(UUID notificationId);
}
