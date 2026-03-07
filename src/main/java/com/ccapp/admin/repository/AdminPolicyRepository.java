package com.ccapp.admin.repository;

import com.ccapp.admin.domain.entity.AdminPolicy;
import com.ccapp.admin.domain.enums.AdminPolicyType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminPolicyRepository extends JpaRepository<AdminPolicy, UUID> {

    List<AdminPolicy> findByPolicyTypeOrderByCreatedAtDesc(AdminPolicyType policyType);
}
