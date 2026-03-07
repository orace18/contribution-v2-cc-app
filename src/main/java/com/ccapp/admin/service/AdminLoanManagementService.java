package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminLoanSummaryResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminLoanManagementService {

    private final LoanRepository loanRepository;

    @Transactional(readOnly = true)
    public List<AdminLoanSummaryResponse> listLoans() {
        return loanRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminLoanSummaryResponse getLoan(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));
        return toSummary(loan);
    }

    @Transactional
    public AdminLoanSummaryResponse approveLoan(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new BusinessException("Only pending loans can be approved");
        }

        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedAt(OffsetDateTime.now());
        return toSummary(loanRepository.save(loan));
    }

    @Transactional
    public AdminLoanSummaryResponse rejectLoan(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));

        if (!(loan.getStatus() == LoanStatus.PENDING || loan.getStatus() == LoanStatus.APPROVED)) {
            throw new BusinessException("Only pending or approved loans can be rejected");
        }

        loan.setStatus(LoanStatus.REJECTED);
        return toSummary(loanRepository.save(loan));
    }

    private AdminLoanSummaryResponse toSummary(Loan loan) {
        return new AdminLoanSummaryResponse(
                loan.getId(),
                loan.getAccountHolderId(),
                loan.getCircleId(),
                loan.getAmount(),
                loan.getCurrency(),
                loan.getInterestRate(),
                loan.getDurationMonths(),
                loan.getStatus(),
                loan.getApprovedAt(),
                loan.getDisbursedAt(),
                loan.getCreatedAt()
        );
    }
}
