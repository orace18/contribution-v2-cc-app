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
        return reserveInternal(accountHolderId, request, false);
    }

    public WalletReserveResponse reserveSystem(UUID accountHolderId, WalletReserveRequest request) {
        return reserveInternal(accountHolderId, request, true);
    }

    public void releaseSystem(UUID accountHolderId, String reason) {
        transitionSystemReservation(accountHolderId, reason, WalletReservationStatus.RELEASED);
    }

    public void consumeSystem(UUID accountHolderId, String reason) {
        transitionSystemReservation(accountHolderId, reason, WalletReservationStatus.CONSUMED);
    }

    private WalletReserveResponse reserveInternal(UUID accountHolderId,
                                                  WalletReserveRequest request,
                                                  boolean systemReservation) {
        Wallet wallet = walletGuardService.requireActiveWallet(accountHolderId);
        walletGuardService.checkDebitAllowed(wallet, request.amount(), request.currency());
        walletSecurityService.authorizeReservation(wallet, accountHolderId, request.pin(), systemReservation);

        if (systemReservation) {
            WalletReservation existingReservation = reservationRepository.findByWalletIdAndStatus(
                            wallet.getId(),
                            WalletReservationStatus.ACTIVE
                    ).stream()
                    .filter(candidate -> request.reason().equals(candidate.getReason()))
                    .findFirst()
                    .orElse(null);
            if (existingReservation != null) {
                log.info("wallet_system_reservation_reused walletId={} reservationId={} amount={} reason={}",
                        wallet.getId(),
                        existingReservation.getId(),
                        existingReservation.getAmount(),
                        existingReservation.getReason());
                return toResponse(wallet, existingReservation);
            }
        }

        WalletReservation reservation = reservationRepository.save(WalletReservation.builder()
                .walletId(wallet.getId())
                .amount(request.amount())
                .reason(request.reason())
                .expiryDate(request.expiryDate())
                .status(WalletReservationStatus.ACTIVE)
                .build());

        eventPublisher.publish(WalletReservedEvent.create(
                wallet.getId(), accountHolderId, request.amount(), reservation.getId()));

        log.info("wallet_reserved walletId={} reservationId={} amount={} reason={}",
                wallet.getId(), reservation.getId(), request.amount(), request.reason());

        return toResponse(wallet, reservation);
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

        reservation.setStatus(WalletReservationStatus.RELEASED);
        reservationRepository.save(reservation);

        eventPublisher.publish(WalletReleasedEvent.create(
                wallet.getId(), reservationId, reservation.getAmount()));

        log.info("wallet_reservation_released reservationId={} walletId={} amount={}",
                reservationId, wallet.getId(), reservation.getAmount());
    }

    private void transitionSystemReservation(UUID accountHolderId,
                                             String reason,
                                             WalletReservationStatus targetStatus) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("System reservation reason is required");
        }
        Wallet wallet = walletGuardService.requireActiveWallet(accountHolderId);
        WalletReservation reservation = reservationRepository.findByWalletIdAndStatus(
                        wallet.getId(),
                        WalletReservationStatus.ACTIVE
                ).stream()
                .filter(candidate -> reason.equals(candidate.getReason()))
                .findFirst()
                .orElse(null);
        if (reservation == null) {
            log.warn("wallet_system_reservation_not_found walletId={} accountHolderId={} reason={} targetStatus={}",
                    wallet.getId(), accountHolderId, reason, targetStatus);
            return;
        }

        reservation.setStatus(targetStatus);
        reservationRepository.save(reservation);

        if (targetStatus == WalletReservationStatus.RELEASED) {
            eventPublisher.publish(WalletReleasedEvent.create(
                    wallet.getId(), reservation.getId(), reservation.getAmount()));
        }

        log.info("wallet_system_reservation_transition reservationId={} walletId={} amount={} targetStatus={} reason={}",
                reservation.getId(), wallet.getId(), reservation.getAmount(), targetStatus, reason);
    }

    private WalletReserveResponse toResponse(Wallet wallet, WalletReservation reservation) {
        return new WalletReserveResponse(
                reservation.getId(),
                wallet.getId(),
                reservation.getAmount(),
                reservation.getStatus().name(),
                reservation.getExpiryDate()
        );
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
}
