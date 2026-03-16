package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.LoanPortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanPortfolioSnapshotRepository extends JpaRepository<LoanPortfolioSnapshot, UUID> {

    Optional<LoanPortfolioSnapshot> findByPartnerIdAndSnapshotDate(UUID partnerId, LocalDate snapshotDate);

    List<LoanPortfolioSnapshot> findByPartnerIdOrderBySnapshotDateDesc(UUID partnerId);
}
