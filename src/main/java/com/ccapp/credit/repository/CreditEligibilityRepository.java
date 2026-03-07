package com.ccapp.credit.repository;

import com.ccapp.credit.domain.entity.CreditEligibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreditEligibilityRepository extends JpaRepository<CreditEligibility, UUID> {
}
