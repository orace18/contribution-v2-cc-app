package com.ccapp.identity.delegation.repository;

import com.ccapp.identity.delegation.domain.entity.DelegatedAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DelegatedActionRepository extends JpaRepository<DelegatedAction, UUID> {
}
