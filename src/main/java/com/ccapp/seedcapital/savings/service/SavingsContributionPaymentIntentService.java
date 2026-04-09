package com.ccapp.seedcapital.savings.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.savings.domain.entity.MemberSavingsContribution;
import com.ccapp.seedcapital.savings.domain.entity.SavingsCycle;
import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsContributionPaymentProvider;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
import com.ccapp.seedcapital.savings.dto.CreateSavingsContributionPaymentIntentRequest;
import com.ccapp.seedcapital.savings.dto.SavingsContributionPaymentIntentResponse;
import com.ccapp.seedcapital.savings.repository.MemberSavingsContributionRepository;
import com.ccapp.wallet.service.WalletDebitFacade;
import com.ccapp.wallet.service.WalletGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavingsContributionPaymentIntentService {

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final SavingsCycleService savingsCycleService;
    private final ImfSavingsChannelService imfSavingsChannelService;
    private final MemberSavingsContributionRepository memberSavingsContributionRepository;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final WalletGuardService walletGuardService;
    private final WalletDebitFacade walletDebitFacade;

    @Transactional
    public SavingsContributionPaymentIntentResponse createMyPaymentIntent(UUID actorUserId,
                                                                         UUID requesterAccountHolderId,
                                                                         UUID circleId,
                                                                         UUID cycleId,
                                                                         CreateSavingsContributionPaymentIntentRequest request) {
        circleReadinessAccessService.assertCircleMember(requesterAccountHolderId, circleId);
        SavingsCycle cycle = savingsCycleService.refreshCycleStatus(savingsCycleService.requireCycle(circleId, cycleId));
        validateCycleState(cycle);
        imfSavingsChannelService.requireReadyChannel(requesterAccountHolderId, cycle.getCountryCode());

        MemberSavingsContribution contribution = memberSavingsContributionRepository.findByCycleIdAndAccountHolderId(cycleId, requesterAccountHolderId)
                .orElseThrow(() -> new BusinessException("Savings contribution expectation not found for circle member"));
        validateContributionEligibility(contribution, request.amount());
        validateProvider(request.provider());
        validateCurrency(cycle, request.currency());

        String destinationAccountCode = countryChartOfAccountsService.findOrCreate(
                cycle.getCountryCode(),
                CountryAccountGroup.PARTNER_SETTLEMENT,
                "IMF savings settlement"
        ).getAccountCode();
        Wallet sourceWallet = walletGuardService.requireActiveWallet(requesterAccountHolderId);

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(
                new WalletDebitFacade.SecureDebitRequest(
                        requesterAccountHolderId,
                        requesterAccountHolderId,
                        actorUserId,
                        sourceWallet,
                        OperationType.WALLET_PAYMENT,
                        request.amount(),
                        normalizeCurrency(request.currency()),
                        request.idempotencyKey().trim(),
                        SavingsContributionPaymentIntentMetadata.buildDescription(circleId, cycleId),
                        SavingsContributionPaymentIntentMetadata.buildFingerprint(circleId, cycleId, request.amount(), request.currency()),
                        destinationAccountCode,
                        null,
                        null,
                        null,
                        request.pin(),
                        request.otpChallengeId(),
                        request.otpCode(),
                        false,
                        false,
                        "Savings contribution - wallet debit",
                        "Savings contribution - partner settlement credit",
                        null,
                        null,
                        WalletDebitFacade.SecureDebitPostingMode.STANDARD,
                        null
                )
        );

        return new SavingsContributionPaymentIntentResponse(
                result.operationId(),
                request.provider(),
                result.otpRequired() ? "OTP_REQUIRED" : "PENDING_CONFIRMATION",
                result.operationId(),
                result.otpChallengeId(),
                result.otpExpiresAt()
        );
    }

    private void validateCycleState(SavingsCycle cycle) {
        if (cycle.getStatus() == SavingsCycleStatus.CANCELLED
                || cycle.getStatus() == SavingsCycleStatus.COMPLETED
                || cycle.getStatus() == SavingsCycleStatus.MISSED) {
            throw new BusinessException("Savings contribution payment cannot be initiated for this cycle status");
        }
    }

    private void validateContributionEligibility(MemberSavingsContribution contribution,
                                                 BigDecimal requestedAmount) {
        BigDecimal confirmedAmount = contribution.getConfirmedAmount() == null
                ? BigDecimal.ZERO.setScale(4)
                : contribution.getConfirmedAmount();
        if (contribution.getStatus() == MemberSavingsContributionStatus.CONFIRMED
                && confirmedAmount.compareTo(contribution.getExpectedAmount()) >= 0) {
            throw new BusinessException("Savings contribution already confirmed for this cycle");
        }
        BigDecimal outstandingAmount = contribution.getExpectedAmount().subtract(confirmedAmount);
        if (outstandingAmount.signum() <= 0) {
            throw new BusinessException("Savings contribution already settled for this cycle");
        }
        if (requestedAmount.compareTo(outstandingAmount) > 0) {
            throw new BusinessException("Requested payment amount exceeds the outstanding savings contribution");
        }
    }

    private void validateProvider(SavingsContributionPaymentProvider provider) {
        if (provider != SavingsContributionPaymentProvider.WALLET) {
            throw new BusinessException("Only WALLET provider is currently supported for savings contribution payment intents");
        }
    }

    private void validateCurrency(SavingsCycle cycle, String currency) {
        String normalizedCurrency = normalizeCurrency(currency);
        if (!normalizedCurrency.equalsIgnoreCase(cycle.getCurrency())) {
            throw new BusinessException("Savings contribution payment currency must match the cycle currency");
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
