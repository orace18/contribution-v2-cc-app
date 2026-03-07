package com.ccapp.credit.repository;

import com.ccapp.credit.domain.entity.RepaymentSchedule;
import com.ccapp.credit.domain.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, UUID> {

    List<RepaymentSchedule> findByLoan_IdOrderByInstallmentNumberAsc(UUID loanId);

    Optional<RepaymentSchedule> findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(UUID loanId, Collection<InstallmentStatus> statuses);

    long countByLoan_IdAndStatusIn(UUID loanId, Collection<InstallmentStatus> statuses);
}
