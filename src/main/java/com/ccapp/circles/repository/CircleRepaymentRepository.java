package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleRepayment;
import com.ccapp.circles.domain.enums.CircleRepaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CircleRepaymentRepository extends JpaRepository<CircleRepayment, UUID> {

    List<CircleRepayment> findByLoan_IdOrderByDueDateAsc(UUID loanId);

    @Query("""
            select coalesce(sum(cr.amount), 0) from CircleRepayment cr
            where cr.loan.id = :loanId
              and cr.status = :status
            """)
    BigDecimal sumByLoanIdAndStatus(UUID loanId, CircleRepaymentStatus status);

    List<CircleRepayment> findByLoan_Borrower_IdOrderByDueDateDesc(UUID borrowerMemberId);
}
