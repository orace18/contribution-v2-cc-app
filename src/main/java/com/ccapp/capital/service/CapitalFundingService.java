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
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
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
    private final CountryContextService countryContextService;

    @Transactional
    public LoanFundingResponse fundLoan(UUID actorUserId, UUID loanId, UUID capitalPoolId) {
        throw new BusinessException("INTERNAL_CAPITAL_FUNDING_DISABLED_FOR_MVP");
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
