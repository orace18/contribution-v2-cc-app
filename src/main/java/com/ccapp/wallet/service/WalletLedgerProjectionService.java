package com.ccapp.wallet.service;

import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.repository.WalletLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletLedgerProjectionService {

    private final WalletLedgerEntryRepository walletLedgerEntryRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public void syncOperation(Operation operation) {
        if (operation == null || operation.getId() == null || operation.getType() == null) {
            return;
        }

        for (LedgerProjection projection : projectionsFor(operation)) {
            WalletLedgerEntry entry = walletLedgerEntryRepository
                    .findByWalletIdAndOperationId(projection.walletId(), operation.getId())
                    .orElseGet(() -> WalletLedgerEntry.builder()
                            .walletId(projection.walletId())
                            .operationId(operation.getId())
                            .build());
            entry.setEntryType(projection.entryType());
            entry.setCategory(projection.category());
            entry.setStatus(mapStatus(operation.getStatus()));
            entry.setAmount(operation.getAmount());
            entry.setCurrency(operation.getCurrency());
            if (entry.getCreatedAt() == null && operation.getCreatedAt() != null) {
                entry.setCreatedAt(operation.getCreatedAt());
            }
            walletLedgerEntryRepository.save(entry);
        }
    }

    private List<LedgerProjection> projectionsFor(Operation operation) {
        List<LedgerProjection> projections = new ArrayList<>();
        switch (operation.getType()) {
            case WALLET_DEPOSIT_PSP, WALLET_DEPOSIT_AGENT -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.CREDIT,
                    WalletLedgerCategory.DEPOSIT
            );
            case WALLET_WITHDRAWAL -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.DEBIT,
                    WalletLedgerCategory.WITHDRAWAL
            );
            case WALLET_PAYMENT -> {
                addAccountHolderProjection(
                        projections,
                        operation.getAccountHolderId(),
                        WalletLedgerEntryType.DEBIT,
                        WalletLedgerCategory.TRANSFER_OUT
                );
                if (operation.getStatus() == OperationStatus.CONFIRMED) {
                    resolveDestinationWallet(operation).ifPresent(wallet -> projections.add(
                            new LedgerProjection(
                                    wallet.getId(),
                                    WalletLedgerEntryType.CREDIT,
                                    WalletLedgerCategory.TRANSFER_IN
                            )
                    ));
                }
            }
            case COMMISSION_PAYOUT -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.CREDIT,
                    WalletLedgerCategory.BONUS
            );
            case LOAN_DISBURSEMENT, MARKETPLACE_MERCHANT_PAYOUT, COMMERCE_DSB_PAYOUT -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.CREDIT,
                    WalletLedgerCategory.PAYOUT
            );
            case ADJUSTMENT -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.CREDIT,
                    WalletLedgerCategory.REFUND
            );
            case SUBSCRIPTION, MARKETPLACE_PAYMENT -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.DEBIT,
                    WalletLedgerCategory.PAYMENT
            );
            case LOAN_REPAYMENT -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.DEBIT,
                    WalletLedgerCategory.REPAYMENT
            );
            case MARKETPLACE_MERCHANT_PAYOUT_REVERSAL, COMMERCE_DSB_REVERSAL -> addAccountHolderProjection(
                    projections,
                    operation.getAccountHolderId(),
                    WalletLedgerEntryType.DEBIT,
                    WalletLedgerCategory.REFUND
            );
            default -> {
            }
        }
        return projections;
    }

    private void addAccountHolderProjection(List<LedgerProjection> projections,
                                            UUID accountHolderId,
                                            WalletLedgerEntryType entryType,
                                            WalletLedgerCategory category) {
        if (accountHolderId == null) {
            return;
        }
        findLatestUserWallet(accountHolderId).ifPresent(wallet -> projections.add(
                new LedgerProjection(wallet.getId(), entryType, category)
        ));
    }

    private Optional<Wallet> resolveDestinationWallet(Operation operation) {
        if (operation.getDestinationWalletId() != null) {
            Optional<Wallet> wallet = walletRepository.findById(operation.getDestinationWalletId());
            if (wallet.isPresent()) {
                return wallet;
            }
        }
        if (operation.getDestinationAccountHolderId() != null) {
            return findLatestUserWallet(operation.getDestinationAccountHolderId());
        }
        return Optional.empty();
    }

    private Optional<Wallet> findLatestUserWallet(UUID accountHolderId) {
        Optional<Wallet> activeUserWallet = walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                accountHolderId,
                WalletType.USER,
                "ACTIVE"
        );
        if (activeUserWallet.isPresent()) {
            return activeUserWallet;
        }
        return walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId)
                .stream()
                .filter(wallet -> wallet.getWalletType() == null || wallet.getWalletType() == WalletType.USER)
                .findFirst();
    }

    private WalletLedgerStatus mapStatus(OperationStatus status) {
        if (status == null) {
            return WalletLedgerStatus.PENDING;
        }
        return switch (status) {
            case CONFIRMED -> WalletLedgerStatus.CONFIRMED;
            case FAILED, EXPIRED, CANCELED -> WalletLedgerStatus.FAILED;
            case INITIATED, PAYOUT_INITIATED, PENDING_PSP -> WalletLedgerStatus.PENDING;
        };
    }

    private record LedgerProjection(
            UUID walletId,
            WalletLedgerEntryType entryType,
            WalletLedgerCategory category
    ) {
    }
}
