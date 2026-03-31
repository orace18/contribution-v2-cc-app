package com.ccapp.identity.repository;

import com.ccapp.identity.domain.entity.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
}
