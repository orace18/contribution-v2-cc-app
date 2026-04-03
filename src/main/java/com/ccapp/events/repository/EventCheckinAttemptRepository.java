package com.ccapp.events.repository;

import com.ccapp.events.domain.entity.EventCheckinAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventCheckinAttemptRepository extends JpaRepository<EventCheckinAttempt, UUID> {

    Optional<EventCheckinAttempt> findByClientScanId(String clientScanId);
}
