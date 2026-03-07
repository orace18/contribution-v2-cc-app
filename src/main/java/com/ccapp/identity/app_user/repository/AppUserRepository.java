package com.ccapp.identity.app_user.repository;

import com.ccapp.identity.app_user.domain.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByFirebaseUid(String firebaseUid);

    List<AppUser> findByAccountHolderId(UUID accountHolderId);
}
