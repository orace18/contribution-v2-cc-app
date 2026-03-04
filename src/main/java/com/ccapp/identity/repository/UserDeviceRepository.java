package com.ccapp.identity.repository;

import com.ccapp.identity.domain.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    Optional<UserDevice> findByUserIdAndDeviceIdentifierAndDeletedFalse(UUID userId, String deviceIdentifier);

    List<UserDevice> findByUserIdAndDeletedFalse(UUID userId);
}
