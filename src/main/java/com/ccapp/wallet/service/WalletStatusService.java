package com.ccapp.wallet.service;

import com.ccapp.financial.access.domain.FinancialAccessDecision;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletStatusService {

    private static final EnumSet<FinancialAccessReasonCode> MEMBERSHIP_BLOCKERS = EnumSet.of(
            FinancialAccessReasonCode.MEMBERSHIP_MISSING,
            FinancialAccessReasonCode.MEMBERSHIP_NOT_ACTIVE,
            FinancialAccessReasonCode.MEMBERSHIP_EXPIRED
    );

    private final WalletRepository walletRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final FinancialAccessPolicy financialAccessPolicy;
    private final WalletActivationService walletActivationService;

    @Transactional
    public WalletStatusSnapshot resolveStatus(UUID accountHolderId, boolean attemptAutoActivation) {
        if (attemptAutoActivation) {
            walletActivationService.activateIfEligible(accountHolderId);
        }

        Wallet activeWallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElse(null);
        boolean phoneVerified = phoneVerificationRepository
                .findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                        accountHolderId,
                        PhoneVerificationStatus.VERIFIED
                )
                .map(verification -> verification.getPhoneNumber() != null && !verification.getPhoneNumber().isBlank())
                .orElse(false);
        FinancialAccessDecision activationDecision =
                financialAccessPolicy.evaluate(accountHolderId, FinancialAccessUseCase.WALLET_ACTIVATION);

        WalletStatusSnapshot snapshot;
        if (activeWallet != null) {
            snapshot = new WalletStatusSnapshot(
                    activeWallet.getId(),
                    true,
                    activeWallet.getPinHash() != null,
                    WalletState.WALLET_ACTIVE,
                    "Wallet is active",
                    phoneVerified,
                    activationDecision.allowed(),
                    activationDecision.reasonCodes()
            );
        } else {
            WalletState state = resolveState(phoneVerified, activationDecision);
            snapshot = new WalletStatusSnapshot(
                    null,
                    false,
                    false,
                    state,
                    resolveMessage(state, activationDecision),
                    phoneVerified,
                    activationDecision.allowed(),
                    activationDecision.reasonCodes()
            );
        }

        log.info("wallet_status_resolved accountHolderId={} state={} walletId={} phoneVerified={} activationEligible={} reasonCodes={}",
                accountHolderId,
                snapshot.state(),
                snapshot.walletId(),
                snapshot.phoneVerified(),
                snapshot.activationEligible(),
                snapshot.blockingReasons());
        return snapshot;
    }

    private WalletState resolveState(boolean phoneVerified, FinancialAccessDecision activationDecision) {
        if (!phoneVerified) {
            return WalletState.VERIFY_PHONE;
        }
        if (activationDecision.allowed()) {
            return WalletState.WAIT_WALLET_ACTIVATION;
        }
        if (activationDecision.reasonCodes().stream().anyMatch(MEMBERSHIP_BLOCKERS::contains)) {
            return WalletState.WAIT_MEMBERSHIP_ACTIVATION;
        }
        return WalletState.WALLET_ACTIVATION_BLOCKED;
    }

    private String resolveMessage(WalletState state, FinancialAccessDecision activationDecision) {
        return switch (state) {
            case VERIFY_PHONE -> "Verify your phone to activate your wallet";
            case WAIT_MEMBERSHIP_ACTIVATION, WALLET_ACTIVATION_BLOCKED -> activationDecision.primaryMessage();
            case WAIT_WALLET_ACTIVATION -> "Wallet activation is in progress";
            case WALLET_ACTIVE -> "Wallet is active";
        };
    }

    public enum WalletState {
        VERIFY_PHONE,
        WAIT_MEMBERSHIP_ACTIVATION,
        WAIT_WALLET_ACTIVATION,
        WALLET_ACTIVE,
        WALLET_ACTIVATION_BLOCKED
    }

    public record WalletStatusSnapshot(
            UUID walletId,
            boolean active,
            boolean hasPin,
            WalletState state,
            String message,
            boolean phoneVerified,
            boolean activationEligible,
            List<FinancialAccessReasonCode> blockingReasons
    ) {
        public WalletStatusSnapshot {
            blockingReasons = List.copyOf(blockingReasons == null ? List.of() : blockingReasons);
        }
    }
}
