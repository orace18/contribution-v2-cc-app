package com.ccapp.capital.service;

import com.ccapp.capital.dto.LoanFundingResponse;
import com.ccapp.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Deprecated(since = "2026-04", forRemoval = false)
public class CapitalFundingService {

    private static final String LEGACY_CAPITAL_MODULE_DISABLED = "LEGACY_CAPITAL_MODULE_DISABLED";

    @Transactional
    public LoanFundingResponse fundLoan(UUID actorUserId, UUID loanId, UUID capitalPoolId) {
        throw new BusinessException(LEGACY_CAPITAL_MODULE_DISABLED);
    }
}
