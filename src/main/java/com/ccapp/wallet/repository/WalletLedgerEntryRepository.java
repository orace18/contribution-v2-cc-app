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
              and e.createdAt >= :createdAtFrom
            """)
    BigDecimal sumAmountByWalletIdAndEntryTypeAndStatusAndCreatedAtGreaterThanEqual(
            @Param("walletId") UUID walletId,
            @Param("entryType") WalletLedgerEntryType entryType,
            @Param("status") WalletLedgerStatus status,
            @Param("createdAtFrom") OffsetDateTime createdAtFrom
    );

    @Query("""
            select coalesce(sum(e.amount), 0)
            from WalletLedgerEntry e
            where e.walletId = :walletId
              and e.category = :category
              and e.status = :status
            """)
    BigDecimal sumAmountByWalletIdAndCategoryAndStatus(
            @Param("walletId") UUID walletId,
            @Param("category") WalletLedgerCategory category,
            @Param("status") WalletLedgerStatus status
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
