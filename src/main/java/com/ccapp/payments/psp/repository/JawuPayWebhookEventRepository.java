package com.ccapp.payments.psp.repository;

import com.ccapp.payments.psp.domain.entity.JawuPayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JawuPayWebhookEventRepository extends JpaRepository<JawuPayWebhookEvent, UUID> {

    boolean existsByEventId(String eventId);

    boolean existsByEventHash(String eventHash);
}
