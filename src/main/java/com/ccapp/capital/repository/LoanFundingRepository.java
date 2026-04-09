package com.ccapp.capital.repository;

import com.ccapp.capital.domain.entity.LoanFunding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Deprecated(since = "2026-04", forRemoval = false)
public interface LoanFundingRepository extends JpaRepository<LoanFunding, UUID> {

    Optional<LoanFunding> findByLoan_Id(UUID loanId);
}
