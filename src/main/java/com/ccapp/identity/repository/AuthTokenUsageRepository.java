package com.ccapp.identity.repository;

import com.ccapp.identity.domain.entity.AuthTokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuthTokenUsageRepository extends JpaRepository<AuthTokenUsage, UUID> {
    boolean existsByTokenHashAndUsedAtAfter(String tokenHash, OffsetDateTime after);

    List<AuthTokenUsage> findByTokenHashOrderByUsedAtDesc(String tokenHash);
}
