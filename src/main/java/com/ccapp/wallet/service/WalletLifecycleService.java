package com.ccapp.wallet.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletLifecycleService {

    private static final List<FinancialAccessReasonCode> PHONE_NOT_VERIFIED_REASON =
            List.of(FinancialAccessReasonCode.PHONE_NOT_VERIFIED);

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public WalletStatusSnapshot resolveStatus(UUID accountHolderId) {
        Wallet activeWallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElse(null);
        boolean phoneVerified = findVerifiedPhoneNumber(accountHolderId).isPresent();

        WalletStatusSnapshot snapshot;
        if (activeWallet != null) {
            snapshot = new WalletStatusSnapshot(
                    activeWallet.getId(),
                    true,
                    activeWallet.getPinHash() != null,
                    WalletState.ACTIVE,
                    "Wallet is active",
                    phoneVerified,
                    true,
                    List.of()
            );
        } else {
            WalletState state = resolveState(phoneVerified);
            snapshot = new WalletStatusSnapshot(
                    null,
                    false,
                    false,
                    state,
                    resolveMessage(state),
                    phoneVerified,
                    phoneVerified,
                    phoneVerified ? List.of() : PHONE_NOT_VERIFIED_REASON
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
        String verifiedPhoneNumber = findVerifiedPhoneNumber(accountHolderId)
                .orElseThrow(() -> new BusinessException(FinancialAccessReasonCode.PHONE_NOT_VERIFIED.name()));

        return activateWithVerifiedPhone(accountHolderId, verifiedPhoneNumber);
    }

    @Transactional
    public Optional<Wallet> activateIfEligible(UUID accountHolderId) {
        return activateIfEligible(accountHolderId, null);
    }

    @Transactional
    public Optional<Wallet> activateIfEligible(UUID accountHolderId, String preferredVerifiedPhoneNumber) {
        Optional<String> verifiedPhoneNumber = resolveVerifiedPhoneNumber(accountHolderId, preferredVerifiedPhoneNumber);
        if (verifiedPhoneNumber.isEmpty()) {
            log.info("wallet_activation_skipped accountHolderId={} reason={}",
                    accountHolderId,
                    FinancialAccessReasonCode.PHONE_NOT_VERIFIED.name());
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

    private WalletState resolveState(boolean phoneVerified) {
        if (!phoneVerified) {
            return WalletState.PHONE_NOT_VERIFIED;
        }
        return WalletState.READY_FOR_WALLET_CREATION;
    }

    private String resolveMessage(WalletState state) {
        return switch (state) {
            case PHONE_NOT_VERIFIED -> "Phone verification required for wallet activation";
            case READY_FOR_WALLET_CREATION -> "Wallet is ready for creation";
            case ACTIVE -> "Wallet is active";
        };
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
