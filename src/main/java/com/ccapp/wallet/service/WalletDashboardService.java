package com.ccapp.wallet.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.dto.WalletBalanceResponse;
import com.ccapp.wallet.dto.WalletDashboardOperationResponse;
import com.ccapp.wallet.dto.WalletDashboardResponse;
import com.ccapp.wallet.repository.WalletLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletDashboardService {

    private static final String ACTIVE_WALLET_STATUS = "ACTIVE";
    private static final int RECENT_OPERATION_LIMIT = 20;
    private static final int RECENT_OPERATION_LOOKUP_LIMIT = 40;
    private static final List<OperationType> RECENT_WALLET_OPERATION_TYPES = List.of(
            OperationType.WALLET_DEPOSIT_PSP,
            OperationType.WALLET_DEPOSIT_AGENT,
            OperationType.WALLET_WITHDRAWAL,
            OperationType.WALLET_PAYMENT,
            OperationType.COMMISSION_PAYOUT,
            OperationType.LOAN_DISBURSEMENT,
            OperationType.MARKETPLACE_MERCHANT_PAYOUT,
            OperationType.COMMERCE_DSB_PAYOUT,
            OperationType.ADJUSTMENT,
            OperationType.SUBSCRIPTION,
            OperationType.MARKETPLACE_PAYMENT,
            OperationType.LOAN_REPAYMENT,
            OperationType.MARKETPLACE_MERCHANT_PAYOUT_REVERSAL,
            OperationType.COMMERCE_DSB_REVERSAL
    );

    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final WalletRepository walletRepository;
    private final WalletLedgerEntryRepository walletLedgerEntryRepository;
    private final WalletBalanceService walletBalanceService;
    private final OperationRepository operationRepository;

    @Transactional(readOnly = true)
    public WalletDashboardResponse getDashboard(UUID actorUserId, UUID accountHolderId) {
        assertCanLoadDashboard(actorUserId, accountHolderId);
        log.debug("Loading wallet dashboard for user {}", actorUserId);

        Wallet wallet = resolveUserWallet(accountHolderId);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfMonth = now
                .with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate()
                .atStartOfDay()
                .atOffset(now.getOffset());

        BigDecimal monthlyIncome = BigDecimal.ZERO;
        BigDecimal totalWithdrawn = BigDecimal.ZERO;

        if (wallet != null) {
            monthlyIncome = walletLedgerEntryRepository
                    .sumAmountByWalletIdAndEntryTypeAndStatusAndCreatedAtGreaterThanEqual(
                            wallet.getId(),
                            WalletLedgerEntryType.CREDIT,
                            WalletLedgerStatus.CONFIRMED,
                            startOfMonth
                    );
            totalWithdrawn = walletLedgerEntryRepository
                    .sumAmountByWalletIdAndCategoryAndStatus(
                            wallet.getId(),
                            WalletLedgerCategory.WITHDRAWAL,
                            WalletLedgerStatus.CONFIRMED
                    );
        }

        // Use the authoritative accounting-ledger balance (same source as GET /wallet/balance).
        // The wallet_ledger_entries projection table can diverge from the accounting journal
        // (e.g. due to stale or erroneous PSP-funded debit entries). Delegating here guarantees
        // both endpoints always return the same balance figure.
        WalletBalanceResponse balanceSnapshot = walletBalanceService.getBalance(accountHolderId, "XOF");

        return new WalletDashboardResponse(
                scaleAmount(balanceSnapshot.mainBalance()),
                scaleAmount(monthlyIncome),
                scaleAmount(totalWithdrawn),
                loadRecentOperations(accountHolderId, wallet)
        );
    }

    @Transactional(readOnly = true)
    public List<WalletDashboardOperationResponse> getRecentOperations(UUID actorUserId, UUID accountHolderId) {
        assertCanLoadDashboard(actorUserId, accountHolderId);
        return loadRecentOperations(accountHolderId, resolveUserWallet(accountHolderId));
    }

    private void assertCanLoadDashboard(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
    }

    private Wallet resolveUserWallet(UUID accountHolderId) {
        return walletRepository
                .findFirstByAccountHolderIdAndWalletTypeAndStatus(accountHolderId, WalletType.USER, ACTIVE_WALLET_STATUS)
                .orElse(null);
    }

    private List<WalletDashboardOperationResponse> loadRecentOperations(UUID accountHolderId, Wallet wallet) {
        List<WalletDashboardOperationResponse> ledgerOperations = wallet == null
                ? List.of()
                : walletLedgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), PageRequest.of(0, RECENT_OPERATION_LOOKUP_LIMIT))
                        .stream()
                        .map(this::toRecentOperation)
                        .toList();
        log.debug("Ledger entries found: {}", ledgerOperations.size());

        Set<UUID> ledgerOperationIds = new HashSet<>();
        for (WalletDashboardOperationResponse operation : ledgerOperations) {
            if (operation.operationId() != null) {
                ledgerOperationIds.add(operation.operationId());
            }
        }

        List<WalletDashboardOperationResponse> mergedOperations = new ArrayList<>(ledgerOperations);
        operationRepository.findRecentWalletOperationsForAccountHolder(
                        accountHolderId,
                        RECENT_WALLET_OPERATION_TYPES,
                        PageRequest.of(0, RECENT_OPERATION_LOOKUP_LIMIT)
                ).stream()
                .filter(operation -> operation.getId() != null)
                .filter(operation -> !ledgerOperationIds.contains(operation.getId()))
                .map(operation -> toOperationFallback(accountHolderId, operation))
                .flatMap(Optional::stream)
                .forEach(mergedOperations::add);

        mergedOperations.sort(Comparator.comparing(
                WalletDashboardOperationResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return mergedOperations.stream()
                .limit(RECENT_OPERATION_LIMIT)
                .toList();
    }

    private WalletDashboardOperationResponse toRecentOperation(WalletLedgerEntry entry) {
        return new WalletDashboardOperationResponse(
                entry.getOperationId(),
                entry.getCategory(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getStatus(),
                entry.getCreatedAt()
        );
    }

    private Optional<WalletDashboardOperationResponse> toOperationFallback(UUID accountHolderId, Operation operation) {
        WalletLedgerCategory type = mapOperationType(accountHolderId, operation);
        if (type == null) {
            return Optional.empty();
        }
        return Optional.of(new WalletDashboardOperationResponse(
                operation.getId(),
                type,
                defaultAmount(operation.getAmount()),
                defaultCurrency(operation.getCurrency()),
                mapStatus(operation.getStatus()),
                operation.getCreatedAt()
        ));
    }

    private WalletLedgerCategory mapOperationType(UUID accountHolderId, Operation operation) {
        if (operation == null || operation.getType() == null) {
            return null;
        }

        return switch (operation.getType()) {
            case WALLET_DEPOSIT_PSP, WALLET_DEPOSIT_AGENT -> WalletLedgerCategory.DEPOSIT;
            case WALLET_WITHDRAWAL -> WalletLedgerCategory.WITHDRAWAL;
            case WALLET_PAYMENT -> {
                if (Objects.equals(accountHolderId, operation.getAccountHolderId())) {
                    yield WalletLedgerCategory.TRANSFER_OUT;
                }
                if (Objects.equals(accountHolderId, operation.getDestinationAccountHolderId())) {
                    yield WalletLedgerCategory.TRANSFER_IN;
                }
                yield null;
            }
            case COMMISSION_PAYOUT -> WalletLedgerCategory.BONUS;
            case LOAN_DISBURSEMENT, MARKETPLACE_MERCHANT_PAYOUT, COMMERCE_DSB_PAYOUT -> WalletLedgerCategory.PAYOUT;
            case ADJUSTMENT, MARKETPLACE_MERCHANT_PAYOUT_REVERSAL, COMMERCE_DSB_REVERSAL -> WalletLedgerCategory.REFUND;
            case SUBSCRIPTION, MARKETPLACE_PAYMENT -> WalletLedgerCategory.PAYMENT;
            case LOAN_REPAYMENT -> WalletLedgerCategory.REPAYMENT;
            default -> null;
        };
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

    private BigDecimal scaleAmount(BigDecimal amount) {
        return defaultAmount(amount).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String defaultCurrency(String currency) {
        return currency == null || currency.isBlank() ? "XOF" : currency;
    }
}
