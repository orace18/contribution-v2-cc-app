package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {
    Optional<InviteCode> findByCode(String code);
    Optional<InviteCode> findByInvite_Id(UUID inviteId);
    boolean existsByCode(String code);
}
