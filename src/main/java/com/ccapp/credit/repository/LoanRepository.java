package com.ccapp.credit.repository;

import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);

    List<Loan> findAllByOrderByCreatedAtDesc();

    long countByAccountHolderIdAndStatus(UUID accountHolderId, LoanStatus status);

    long countByAccountHolderIdAndStatusIn(UUID accountHolderId, Collection<LoanStatus> statuses);

    long countByStatusIn(Collection<LoanStatus> statuses);
}
