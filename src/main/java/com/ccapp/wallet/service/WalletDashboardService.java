package com.ccapp.wallet.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.dto.WalletDashboardOperationResponse;
import com.ccapp.wallet.dto.WalletDashboardResponse;
import com.ccapp.wallet.repository.WalletLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletDashboardService {

    private static final List<WalletLedgerCategory> MONTHLY_INCOME_CATEGORIES = List.of(
            WalletLedgerCategory.DEPOSIT,
            WalletLedgerCategory.TRANSFER_IN,
            WalletLedgerCategory.PAYOUT,
            WalletLedgerCategory.BONUS
    );

    private static final List<WalletLedgerCategory> TOTAL_WITHDRAWN_CATEGORIES = List.of(
            WalletLedgerCategory.WITHDRAWAL,
            WalletLedgerCategory.TRANSFER_OUT
    );

    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final WalletRepository walletRepository;
    private final WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Transactional(readOnly = true)
    public WalletDashboardResponse getDashboard(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);

        Wallet wallet = walletRepository
                .findFirstByAccountHolderIdAndWalletTypeAndStatus(accountHolderId, WalletType.USER, "ACTIVE")
                .orElse(null);
        if (wallet == null) {
            return new WalletDashboardResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfMonth = now
                .with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate()
                .atStartOfDay()
                .atOffset(now.getOffset());

        BigDecimal monthlyIncome = walletLedgerEntryRepository
                .sumAmountByWalletIdAndEntryTypeAndStatusAndCategoryInAndCreatedAtGreaterThanEqual(
                        wallet.getId(),
                        WalletLedgerEntryType.CREDIT,
                        WalletLedgerStatus.CONFIRMED,
                        MONTHLY_INCOME_CATEGORIES,
                        startOfMonth
                );
        BigDecimal totalWithdrawn = walletLedgerEntryRepository
                .sumAmountByWalletIdAndEntryTypeAndStatusAndCategoryIn(
                        wallet.getId(),
                        WalletLedgerEntryType.DEBIT,
                        WalletLedgerStatus.CONFIRMED,
                        TOTAL_WITHDRAWN_CATEGORIES
                );
        BigDecimal totalCredits = walletLedgerEntryRepository
                .sumAmountByWalletIdAndEntryTypeAndStatus(
                        wallet.getId(),
                        WalletLedgerEntryType.CREDIT,
                        WalletLedgerStatus.CONFIRMED
                );
        BigDecimal totalDebits = walletLedgerEntryRepository
                .sumAmountByWalletIdAndEntryTypeAndStatus(
                        wallet.getId(),
                        WalletLedgerEntryType.DEBIT,
                        WalletLedgerStatus.CONFIRMED
                );

        List<WalletDashboardOperationResponse> recentOperations = walletLedgerEntryRepository
                .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), PageRequest.of(0, 10))
                .stream()
                .map(this::toRecentOperation)
                .toList();

        return new WalletDashboardResponse(
                totalCredits.subtract(totalDebits).setScale(4, RoundingMode.HALF_UP),
                monthlyIncome.setScale(4, RoundingMode.HALF_UP),
                totalWithdrawn.setScale(4, RoundingMode.HALF_UP),
                recentOperations
        );
    }

    private WalletDashboardOperationResponse toRecentOperation(WalletLedgerEntry entry) {
        return new WalletDashboardOperationResponse(
                entry.getOperationId(),
                entry.getEntryType(),
                entry.getCategory(),
                entry.getAmount(),
                entry.getStatus(),
                entry.getCreatedAt()
        );
    }
}
