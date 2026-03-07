package com.ccapp.credit.repository;

import com.ccapp.credit.domain.entity.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

    List<LoanRepayment> findByLoan_IdOrderByPaidAtAsc(UUID loanId);

    Optional<LoanRepayment> findByTransactionId(String transactionId);
}
