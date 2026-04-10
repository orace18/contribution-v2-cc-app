package com.ccapp.financial.access.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financial.access.domain.FinancialAccessDecision;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialAccessPolicy {

    private final MemberFinancialProfileService memberFinancialProfileService;

    @Transactional(readOnly = true)
    public FinancialAccessDecision evaluate(UUID accountHolderId, FinancialAccessUseCase useCase) {
        MemberFinancialProfile profile = memberFinancialProfileService.getProfile(accountHolderId);
        LinkedHashSet<FinancialAccessReasonCode> reasonCodes = new LinkedHashSet<>(profile.blockingReasons());

        if (requiresActiveWallet(useCase)) {
            appendWalletRequirement(profile, reasonCodes);
        }

        List<FinancialAccessReasonCode> orderedReasonCodes = new ArrayList<>(reasonCodes);
        List<String> messages = orderedReasonCodes.stream()
                .map(reasonCode -> messageFor(reasonCode, useCase))
                .toList();
        boolean allowed = orderedReasonCodes.isEmpty();
        return new FinancialAccessDecision(
                accountHolderId,
                useCase,
                allowed ? FinancialAccessState.FINANCIAL_ALLOWED : FinancialAccessState.FINANCIAL_DENIED,
                allowed,
                orderedReasonCodes,
                messages,
                profile
        );
    }

    @Transactional(readOnly = true)
    public boolean isAllowed(UUID accountHolderId, FinancialAccessUseCase useCase) {
        return evaluate(accountHolderId, useCase).allowed();
    }

    @Transactional(readOnly = true)
    public void assertAllowed(UUID accountHolderId, FinancialAccessUseCase useCase) {
        FinancialAccessDecision decision = evaluate(accountHolderId, useCase);
        if (!decision.allowed()) {
            throw new BusinessException(decision.primaryMessage());
        }
    }

    private boolean requiresActiveWallet(FinancialAccessUseCase useCase) {
        return useCase == FinancialAccessUseCase.NETWORK_SPONSOR_ELIGIBILITY;
    }

    private void appendWalletRequirement(MemberFinancialProfile profile,
                                         LinkedHashSet<FinancialAccessReasonCode> reasonCodes) {
        if (profile.walletActive()) {
            return;
        }
        if (profile.walletStatus() == null || profile.walletStatus().isBlank()) {
            reasonCodes.add(FinancialAccessReasonCode.WALLET_MISSING);
            return;
        }
        reasonCodes.add(FinancialAccessReasonCode.WALLET_NOT_ACTIVE);
    }

    private String messageFor(FinancialAccessReasonCode reasonCode, FinancialAccessUseCase useCase) {
        return switch (reasonCode) {
            case ACCOUNT_SUSPENDED -> "Account is suspended";
            case ACCOUNT_BLOCKED -> "Account is blocked";
            case KYC_NOT_VERIFIED -> "KYC must be verified for " + labelFor(useCase);
            case MEMBERSHIP_MISSING -> "Active membership required for " + labelFor(useCase);
            case MEMBERSHIP_NOT_ACTIVE -> "Membership is not active for " + labelFor(useCase);
            case MEMBERSHIP_EXPIRED -> "Membership has expired for " + labelFor(useCase);
            case WALLET_MISSING -> "Active wallet required for " + labelFor(useCase);
            case WALLET_NOT_ACTIVE -> "Wallet is not active for " + labelFor(useCase);
        };
    }

    private String labelFor(FinancialAccessUseCase useCase) {
        return switch (useCase) {
            case WALLET_ACTIVATION -> "wallet activation";
            case CREDIT_APPLICATION -> "credit application";
            case SEED_CAPITAL_ACCESS -> "seed capital access";
            case CIRCLE_FINANCIAL_PARTICIPATION -> "circle financial participation";
            case NETWORK_REWARDED_ACCESS -> "network rewards";
            case NETWORK_SPONSOR_ELIGIBILITY -> "sponsor eligibility";
        };
    }
}
