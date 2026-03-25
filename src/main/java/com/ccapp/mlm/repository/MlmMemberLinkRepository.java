package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmMemberLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MlmMemberLinkRepository extends JpaRepository<MlmMemberLink, UUID> {
}
