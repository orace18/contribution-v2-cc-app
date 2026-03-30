package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.wallet.domain.entity.WalletReservation;
import com.ccapp.wallet.domain.enums.WalletReservationStatus;
import com.ccapp.wallet.dto.WalletReserveRequest;
import com.ccapp.wallet.dto.WalletReserveResponse;
import com.ccapp.wallet.event.WalletReleasedEvent;
import com.ccapp.wallet.event.WalletReservedEvent;
import com.ccapp.wallet.repository.WalletReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages wallet fund reservations (escrow layer).
 *
 * <p>Reserved funds reduce {@code available_balance} without touching the ledger.
 * Only released funds become spendable again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletReservationService {

    private final WalletReservationRepository reservationRepository;
    private final WalletGuardService walletGuardService;
    private final WalletSecurityService walletSecurityService;
    private final DomainEventPublisher eventPublisher;

    /** Creates a fund reservation after verifying available balance. */
    public WalletReserveResponse reserve(UUID accountHolderId, WalletReserveRequest request) {
        return reserveInternal(accountHolderId, request, false, null);
    }

    public WalletReserveResponse reserveSystem(UUID accountHolderId, WalletReserveRequest request) {
        return reserveInternal(accountHolderId, request, true, null);
    }

    public WalletReserveResponse reserveSystem(UUID accountHolderId,
                                               WalletReserveRequest request,
                                               UUID operationId) {
        return reserveInternal(accountHolderId, request, true, operationId);
    }

    private WalletReserveResponse reserveInternal(UUID accountHolderId,
                                                  WalletReserveRequest request,
                                                  boolean systemReservation,
                                                  UUID operationId) {
        if (operationId != null) {
            WalletReservation existing = reservationRepository.findByOperationId(operationId).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == WalletReservationStatus.ACTIVE) {
                    return toResponse(existing);
                }
                throw new BusinessException("Reservation already exists for operation " + operationId
                        + " with status " + existing.getStatus());
            }
        }

        Wallet wallet = walletGuardService.requireActiveWallet(accountHolderId);
        walletGuardService.checkDebitAllowed(wallet, request.amount(), request.currency());
        walletSecurityService.authorizeReservation(wallet, accountHolderId, request.pin(), systemReservation);

        WalletReservation reservation = reservationRepository.save(WalletReservation.builder()
                .walletId(wallet.getId())
                .operationId(operationId)
                .amount(request.amount())
                .reason(request.reason())
                .expiryDate(request.expiryDate())
                .status(WalletReservationStatus.ACTIVE)
                .build());

        eventPublisher.publish(WalletReservedEvent.create(
                wallet.getId(), accountHolderId, request.amount(), reservation.getId()));

        log.info("wallet_reserved walletId={} reservationId={} amount={} reason={}",
                wallet.getId(), reservation.getId(), request.amount(), request.reason());

        return toResponse(reservation);
    }

    /** Releases an ACTIVE reservation, making the funds spendable again. */
    public void release(UUID reservationId, UUID accountHolderId) {
        WalletReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException("Reservation not found: " + reservationId));

        Wallet wallet = walletGuardService.requireActiveWallet(accountHolderId);
        if (!reservation.getWalletId().equals(wallet.getId())) {
            throw new BusinessException("Reservation does not belong to this wallet");
        }
        if (reservation.getStatus() != WalletReservationStatus.ACTIVE) {
            throw new BusinessException("Reservation is not active: " + reservation.getStatus());
        }

        releaseActiveReservation(reservation);
    }

    public void releaseReservation(UUID operationId) {
        if (operationId == null) {
            log.info("wallet_reservation_skip_release operationId=null");
            return;
        }
        WalletReservation reservation = reservationRepository.findByOperationId(operationId).orElse(null);
        if (reservation == null) {
            log.info("wallet_reservation_not_found_for_operation operationId={}", operationId);
            return;
        }
        if (reservation.getStatus() != WalletReservationStatus.ACTIVE) {
            log.info("wallet_reservation_skip_release operationId={} reservationId={} status={}",
                    operationId, reservation.getId(), reservation.getStatus());
            return;
        }
        releaseActiveReservation(reservation);
    }

    /** Marks all ACTIVE reservations past their expiry date as EXPIRED. */
    @Scheduled(cron = "${wallet.reservations.expire.cron:0 */2 * * * *}", zone = "${wallet.reservations.zone:Europe/Paris}")
    public void expireStaleReservations() {
        List<WalletReservation> stale = reservationRepository
                .findExpiredActiveReservations(OffsetDateTime.now());
        for (WalletReservation r : stale) {
            r.setStatus(WalletReservationStatus.EXPIRED);
            reservationRepository.save(r);
            log.info("wallet_reservation_expired reservationId={} walletId={}", r.getId(), r.getWalletId());
        }
    }

    /** Total reserved amount for a given wallet (used in balance calculation). */
    @Transactional(readOnly = true)
    public BigDecimal getTotalReserved(UUID walletId) {
        return reservationRepository.sumSpendBlockingReservationsByWalletId(walletId, OffsetDateTime.now())
                .orElse(BigDecimal.ZERO);
    }

    private WalletReserveResponse toResponse(WalletReservation reservation) {
        return new WalletReserveResponse(
                reservation.getId(),
                reservation.getWalletId(),
                reservation.getAmount(),
                reservation.getStatus().name(),
                reservation.getExpiryDate()
        );
    }

    private void releaseActiveReservation(WalletReservation reservation) {
        reservation.setStatus(WalletReservationStatus.RELEASED);
        reservationRepository.save(reservation);
        eventPublisher.publish(WalletReleasedEvent.create(
                reservation.getWalletId(), reservation.getId(), reservation.getAmount()));
        log.info("wallet_reservation_released reservationId={} walletId={} amount={}",
                reservation.getId(), reservation.getWalletId(), reservation.getAmount());
    }
}
