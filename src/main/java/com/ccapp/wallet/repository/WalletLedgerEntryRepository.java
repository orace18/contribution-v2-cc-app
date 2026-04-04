package com.ccapp.wallet.repository;

import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletLedgerEntryRepository extends JpaRepository<WalletLedgerEntry, UUID> {

    Optional<WalletLedgerEntry> findByWalletIdAndOperationId(UUID walletId, UUID operationId);

    List<WalletLedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    @Query("""
            select coalesce(sum(e.amount), 0)
            from WalletLedgerEntry e
            where e.walletId = :walletId
              and e.entryType = :entryType
              and e.status = :status
              and e.category in :categories
              and e.createdAt >= :createdAtFrom
            """)
    BigDecimal sumAmountByWalletIdAndEntryTypeAndStatusAndCategoryInAndCreatedAtGreaterThanEqual(
            @Param("walletId") UUID walletId,
            @Param("entryType") WalletLedgerEntryType entryType,
            @Param("status") WalletLedgerStatus status,
            @Param("categories") Collection<WalletLedgerCategory> categories,
            @Param("createdAtFrom") OffsetDateTime createdAtFrom
    );

    @Query("""
            select coalesce(sum(e.amount), 0)
            from WalletLedgerEntry e
            where e.walletId = :walletId
              and e.entryType = :entryType
              and e.status = :status
              and e.category in :categories
            """)
    BigDecimal sumAmountByWalletIdAndEntryTypeAndStatusAndCategoryIn(
            @Param("walletId") UUID walletId,
            @Param("entryType") WalletLedgerEntryType entryType,
            @Param("status") WalletLedgerStatus status,
            @Param("categories") Collection<WalletLedgerCategory> categories
    );

    @Query("""
            select coalesce(sum(e.amount), 0)
            from WalletLedgerEntry e
            where e.walletId = :walletId
              and e.entryType = :entryType
              and e.status = :status
            """)
    BigDecimal sumAmountByWalletIdAndEntryTypeAndStatus(
            @Param("walletId") UUID walletId,
            @Param("entryType") WalletLedgerEntryType entryType,
            @Param("status") WalletLedgerStatus status
    );
}
