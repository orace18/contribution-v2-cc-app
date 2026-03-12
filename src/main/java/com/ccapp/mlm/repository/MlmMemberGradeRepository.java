package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmMemberGradeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MlmMemberGradeRepository extends JpaRepository<MlmMemberGradeProfile, UUID> {
}
