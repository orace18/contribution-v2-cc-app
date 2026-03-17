package com.ccapp.platform.config.repository;

import com.ccapp.platform.config.domain.entity.PlatformConfigVersion;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PlatformConfigVersionRepository extends JpaRepository<PlatformConfigVersion, UUID> {

    Optional<PlatformConfigVersion> findFirstByConfigTypeOrderByVersionDesc(PlatformConfigType configType);

    Optional<PlatformConfigVersion> findFirstByConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
            PlatformConfigType configType,
            OffsetDateTime asOf
    );
}
