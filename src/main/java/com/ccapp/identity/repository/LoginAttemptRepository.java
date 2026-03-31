package com.ccapp.identity.repository;

import com.ccapp.identity.domain.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
    long countByIpAddressAndCreatedAtAfter(String ipAddress, OffsetDateTime after);

    long countByDeviceIdentifierAndCreatedAtAfter(String deviceIdentifier, OffsetDateTime after);
}
