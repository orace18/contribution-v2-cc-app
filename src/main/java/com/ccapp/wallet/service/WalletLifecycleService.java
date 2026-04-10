package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financial.access.domain.FinancialAccessDecision;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.wallet.service.WalletService;
import com.ccapp.wallet.domain.enums.WalletType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletLifecycleService {

    private static final EnumSet<FinancialAccessReasonCode> MEMBERSHIP_BLOCKERS = EnumSet.of(
            FinancialAccessReasonCode.MEMBERSHIP_MISSING,
            FinancialAccessReasonCode.MEMBERSHIP_NOT_ACTIVE,
            FinancialAccessReasonCode.MEMBERSHIP_EXPIRED
    );

    private final FinancialAccessPolicy financialAccessPolicy;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public WalletStatusSnapshot resolveStatus(UUID accountHolderId) {
        Wallet activeWallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElse(null);
        boolean phoneVerified = findVerifiedPhoneNumber(accountHolderId).isPresent();
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

    @Transactional
    public Wallet activate(UUID accountHolderId) {
        financialAccessPolicy.assertAllowed(accountHolderId, FinancialAccessUseCase.WALLET_ACTIVATION);

        String verifiedPhoneNumber = findVerifiedPhoneNumber(accountHolderId)
                .orElseThrow(() -> new BusinessException("Phone verification required"));

        return activateWithVerifiedPhone(accountHolderId, verifiedPhoneNumber);
    }

    @Transactional
    public Optional<Wallet> activateIfEligible(UUID accountHolderId) {
        return activateIfEligible(accountHolderId, null);
    }

    @Transactional
    public Optional<Wallet> activateIfEligible(UUID accountHolderId, String preferredVerifiedPhoneNumber) {
        FinancialAccessDecision decision = financialAccessPolicy.evaluate(accountHolderId, FinancialAccessUseCase.WALLET_ACTIVATION);
        if (!decision.allowed()) {
            log.info("wallet_activation_skipped accountHolderId={} reason=financial_access_denied reasonCodes={} message={}",
                    accountHolderId,
                    decision.reasonCodes(),
                    decision.primaryMessage());
            return Optional.empty();
        }

        Optional<String> verifiedPhoneNumber = resolveVerifiedPhoneNumber(accountHolderId, preferredVerifiedPhoneNumber);
        if (verifiedPhoneNumber.isEmpty()) {
            log.info("wallet_activation_skipped accountHolderId={} reason=phone_not_verified", accountHolderId);
            return Optional.empty();
        }

        return verifiedPhoneNumber.map(phoneNumber -> activateWithVerifiedPhone(accountHolderId, phoneNumber));
    }

    private Wallet activateWithVerifiedPhone(UUID accountHolderId, String verifiedPhoneNumber) {
        String normalizedPhone = verifiedPhoneNumber.trim();
        Wallet walletByPhone = walletRepository.findByMobileMoneyNumber(normalizedPhone).orElse(null);
        Wallet activeWallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(
                accountHolderId,
                "ACTIVE"
        ).orElse(null);

        if (walletByPhone != null && !walletByPhone.getAccountHolderId().equals(accountHolderId)) {
            throw new BusinessException("PHONE_ALREADY_BOUND_TO_ANOTHER_WALLET");
        }

        Wallet resolvedWallet = activeWallet;
        if (resolvedWallet != null) {
            if (resolvedWallet.getMobileMoneyNumber() == null
                    || resolvedWallet.getMobileMoneyNumber().isBlank()
                    || !resolvedWallet.getMobileMoneyNumber().trim().equals(normalizedPhone)) {
                resolvedWallet.setMobileMoneyNumber(normalizedPhone);
                resolvedWallet = walletRepository.save(resolvedWallet);
                log.info("wallet_activation_bound_phone accountHolderId={} walletId={} phone={}",
                        accountHolderId,
                        resolvedWallet.getId(),
                        normalizedPhone);
            }
        } else {
            if (walletByPhone != null) {
                throw new BusinessException("WALLET_PHONE_BINDING_REQUIRES_MANUAL_REVIEW");
            }
            resolvedWallet = walletService.createWallet(accountHolderId, "MOBILE", normalizedPhone, "ACTIVE");
            resolvedWallet.setWalletType(WalletType.USER);
            resolvedWallet = walletRepository.save(resolvedWallet);
            log.info("wallet_activation_created accountHolderId={} walletId={} phone={}",
                    accountHolderId,
                    resolvedWallet.getId(),
                    normalizedPhone);
        }

        log.info("wallet_activation_completed accountHolderId={} walletId={}",
                accountHolderId,
                resolvedWallet.getId());
        return resolvedWallet;
    }

    private Optional<String> resolveVerifiedPhoneNumber(UUID accountHolderId, String preferredVerifiedPhoneNumber) {
        String normalizedPreferredPhone = normalizeOptional(preferredVerifiedPhoneNumber);
        if (normalizedPreferredPhone != null) {
            return Optional.of(normalizedPreferredPhone);
        }
        return findVerifiedPhoneNumber(accountHolderId);
    }

    private Optional<String> findVerifiedPhoneNumber(UUID accountHolderId) {
        return phoneVerificationRepository
                .findTopByRequesterAccountHolderIdAndStatusOrderByVerifiedAtDescCreatedAtDesc(
                        accountHolderId,
                        PhoneVerificationStatus.VERIFIED
                )
                .map(verification -> verification.getPhoneNumber() == null ? null : verification.getPhoneNumber().trim())
                .filter(phoneNumber -> phoneNumber != null && !phoneNumber.isBlank());
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

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
