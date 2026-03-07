package com.ccapp.notifications.repository;

import com.ccapp.notifications.domain.entity.NotificationTemplate;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByCodeAndChannelAndActiveTrue(String code, NotificationChannel channel);
}
