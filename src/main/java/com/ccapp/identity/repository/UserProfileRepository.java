package com.ccapp.identity.repository;

import com.ccapp.identity.domain.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByUserId(UUID userId);

    @Query("select p from UserProfile p where p.user.id in :userIds")
    List<UserProfile> findByUserIds(Collection<UUID> userIds);
}
