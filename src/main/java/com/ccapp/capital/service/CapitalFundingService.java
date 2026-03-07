package com.ccapp.capital.service;

import com.ccapp.capital.domain.entity.CapitalPool;
import com.ccapp.capital.domain.entity.Fund;
import com.ccapp.capital.domain.entity.LoanFunding;
import com.ccapp.capital.dto.LoanFundingResponse;
import com.ccapp.capital.repository.CapitalPoolRepository;
import com.ccapp.capital.repository.LoanFundingRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.service.LoanService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CapitalFundingService {

    private final LoanFundingRepository loanFundingRepository;
    private final LoanRepository loanRepository;
    private final CapitalPoolRepository capitalPoolRepository;
    private final AccessControlService accessControlService;
    private final LoanService loanService;

    @Transactional
    public LoanFundingResponse fundLoan(UUID actorUserId, UUID loanId, UUID capitalPoolId) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));

        accessControlService.assertCanActOnAccountHolder(actorUserId, loan.getAccountHolderId(), PermissionLevel.TRANSACT);

        if (loan.getStatus() == LoanStatus.REJECTED) {
            throw new BusinessException("Rejected loan cannot be funded");
        }
        if (loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.DISBURSED || loan.getStatus() == LoanStatus.REPAID) {
            throw new ConflictException("Loan already funded or closed");
        }
        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new BusinessException("Loan must be approved before funding");
        }

        if (loanFundingRepository.findByLoan_Id(loanId).isPresent()) {
            throw new ConflictException("Loan already has funding");
        }

        CapitalPool pool = capitalPoolRepository.findById(capitalPoolId)
                .orElseThrow(() -> new NotFoundException("Capital pool not found"));

        if (!pool.getCircleId().equals(loan.getCircleId())) {
            throw new BusinessException("Capital pool circle does not match loan circle");
        }

        Fund fund = pool.getFund();
        if (!fund.getCurrency().equalsIgnoreCase(loan.getCurrency())) {
            throw new BusinessException("Fund currency must match loan currency");
        }

        BigDecimal remainingPool = pool.getAllocatedAmount().subtract(pool.getUsedAmount());
        if (remainingPool.compareTo(loan.getAmount()) < 0) {
            throw new BusinessException("Insufficient capital in pool");
        }

        pool.setUsedAmount(pool.getUsedAmount().add(loan.getAmount()));
        capitalPoolRepository.save(pool);

        LoanFunding funding = loanFundingRepository.save(LoanFunding.builder()
                .loan(loan)
                .fund(fund)
                .fundedAmount(loan.getAmount())
                .build());

        loanService.disburseApprovedLoan(loan.getId(), actorUserId, "capital-funding:" + funding.getId());

        return toResponse(funding);
    }

    private LoanFundingResponse toResponse(LoanFunding funding) {
        return new LoanFundingResponse(
                funding.getId(),
                funding.getLoan().getId(),
                funding.getFund().getId(),
                funding.getFundedAmount(),
                funding.getFundedAt()
        );
    }
}
