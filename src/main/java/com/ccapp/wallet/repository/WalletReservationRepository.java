package com.ccapp.wallet.repository;

import com.ccapp.wallet.domain.entity.WalletReservation;
import com.ccapp.wallet.domain.enums.WalletReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletReservationRepository extends JpaRepository<WalletReservation, UUID> {

    List<WalletReservation> findByWalletIdAndStatus(UUID walletId, WalletReservationStatus status);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM WalletReservation r " +
            "WHERE r.walletId = :walletId AND r.status = 'ACTIVE' " +
            "AND (r.expiryDate IS NULL OR r.expiryDate > :now)")
    Optional<BigDecimal> sumSpendBlockingReservationsByWalletId(@Param("walletId") UUID walletId,
                                                                @Param("now") OffsetDateTime now);

    @Query("SELECT r FROM WalletReservation r " +
            "WHERE r.status = 'ACTIVE' AND r.expiryDate IS NOT NULL AND r.expiryDate < :now")
    List<WalletReservation> findExpiredActiveReservations(@Param("now") OffsetDateTime now);
}
