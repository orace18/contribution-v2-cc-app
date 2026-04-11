package com.ccapp.payments.subscription.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipReconciliationService {

    private final MembershipRepository membershipRepository;
    private final OperationRepository operationRepository;
    private final MembershipService membershipService;
    private final MobileOnboardingJourneyRepository journeyRepository;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final ObjectProvider<SubscriptionPaymentFinalizationService> subscriptionPaymentFinalizationServiceProvider;
    private final ObjectProvider<MobileOnboardingJourneyService> mobileOnboardingJourneyServiceProvider;

    @Value("${membership.reconciliation.batch-size:50}")
    private int batchSize = 50;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconciliationResult finalizeMembershipForOperation(UUID operationId) {
        return reconcileOperation(operationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconciliationResult reconcileOperation(UUID operationId) {
        Membership membership = membershipRepository.findByOperationId(operationId)
                .orElseThrow(() -> new BusinessException("Membership not found for operation"));
        return reconcileMembershipInternal(membership);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconciliationResult reconcileMembership(UUID membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new BusinessException("Membership not found"));
        return reconcileMembershipInternal(membership);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ReconciliationResult> reconcilePendingMembershipBatch() {
        List<Membership> pendingMemberships = membershipRepository.findByStatusOrderByCreatedAtAsc(
                MembershipStatus.PENDING,
                PageRequest.of(0, Math.max(1, batchSize))
        );
        if (pendingMemberships.isEmpty()) {
            return List.of();
        }

        List<ReconciliationResult> results = new ArrayList<>();
        for (Membership membership : pendingMemberships) {
            try {
                results.add(reconcileMembershipInternal(membership));
            } catch (Exception ex) {
                log.error("membership_reconciliation_failed membershipId={} operationId={} message={}",
                        membership.getId(),
                        membership.getOperationId(),
                        ex.getMessage(),
                        ex);
                results.add(new ReconciliationResult(
                        membership.getId(),
                        membership.getOperationId(),
                        membership.getStatus().name(),
                        membership.getStatus().name(),
                        "ERROR"
                ));
            }
        }
        return List.copyOf(results);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileAccountHolder(UUID accountHolderId) {
        membershipRepository.findByAccountHolderIdAndStatusOrderByCreatedAtAsc(accountHolderId, MembershipStatus.PENDING)
                .forEach(membership -> {
                    try {
                        reconcileMembershipInternal(membership);
                    } catch (Exception ex) {
                        log.error("membership_reconciliation_account_holder_failed membershipId={} accountHolderId={} operationId={} message={}",
                                membership.getId(),
                                accountHolderId,
                                membership.getOperationId(),
                                ex.getMessage(),
                                ex);
                    }
                });

        membershipRepository.findFirstByAccountHolderIdOrderByExpiresAtDescCreatedAtDesc(accountHolderId)
                .ifPresent(membership -> {
                    try {
                        replayFinalizationIfNeeded(membership);
                    } catch (Exception ex) {
                        log.error("membership_reconciliation_replay_failed membershipId={} accountHolderId={} operationId={} message={}",
                                membership.getId(),
                                accountHolderId,
                                membership.getOperationId(),
                                ex.getMessage(),
                                ex);
                    }
                });
    }

    private ReconciliationResult reconcileMembershipInternal(Membership membership) {
        Operation operation = operationRepository.findById(membership.getOperationId()).orElse(null);
        if (operation == null) {
            log.warn("membership_reconciliation_skipped membershipId={} operationId={} reason=operation_missing",
                    membership.getId(),
                    membership.getOperationId());
            return new ReconciliationResult(
                    membership.getId(),
                    membership.getOperationId(),
                    membership.getStatus().name(),
                    membership.getStatus().name(),
                    "OPERATION_MISSING"
            );
        }

        MembershipStatus previousStatus = membership.getStatus();
        if (membership.getStatus() == MembershipStatus.PENDING) {
            Membership resolvedMembership = reconcilePendingMembership(membership, operation);
            return new ReconciliationResult(
                    resolvedMembership.getId(),
                    operation.getId(),
                    previousStatus.name(),
                    resolvedMembership.getStatus().name(),
                    resolvePendingAction(operation.getStatus(), resolvedMembership.getStatus())
            );
        }

        replayFinalizationIfNeeded(membership);
        Membership refreshed = membershipRepository.findById(membership.getId()).orElse(membership);
        return new ReconciliationResult(
                refreshed.getId(),
                operation.getId(),
                previousStatus.name(),
                refreshed.getStatus().name(),
                previousStatus == refreshed.getStatus() ? "NOOP" : "REPLAYED"
        );
    }

    private Membership reconcilePendingMembership(Membership membership, Operation operation) {
        switch (operation.getStatus()) {
            case CONFIRMED -> {
                log.info("membership_reconciliation_confirmed membershipId={} operationId={} action=finalize",
                        membership.getId(),
                        operation.getId());
                subscriptionPaymentFinalizationServiceProvider.getObject().finalizeConfirmedOperation(operation.getId());
                return membershipRepository.findByOperationId(operation.getId()).orElse(membership);
            }
            case FAILED, EXPIRED, CANCELED -> {
                log.info("membership_reconciliation_terminal_failure membershipId={} operationId={} operationStatus={} action=mark_failed",
                        membership.getId(),
                        operation.getId(),
                        operation.getStatus());
                Membership failedMembership = membershipService.failPendingMembership(operation.getId());
                MobileOnboardingJourneyService onboardingJourneyService = mobileOnboardingJourneyServiceProvider.getIfAvailable();
                if (onboardingJourneyService != null) {
                    onboardingJourneyService.onPaidPaymentFailed(operation);
                }
                return failedMembership;
            }
            default -> {
                log.info("membership_reconciliation_waiting membershipId={} operationId={} operationStatus={} action=keep_pending",
                        membership.getId(),
                        operation.getId(),
                        operation.getStatus());
                return membership;
            }
        }
    }

    private void replayFinalizationIfNeeded(Membership membership) {
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return;
        }

        Operation operation = operationRepository.findById(membership.getOperationId()).orElse(null);
        if (operation == null || operation.getStatus() != OperationStatus.CONFIRMED) {
            return;
        }

        MobileOnboardingJourney journey = journeyRepository.findByAccountHolderId(membership.getAccountHolderId())
                .orElse(null);
        if (!requiresFinalizationReplay(membership, journey, operation)) {
            return;
        }

        log.info("membership_reconciliation_replay membershipId={} operationId={} accountHolderId={} journeyStatus={} networkLinked={}",
                membership.getId(),
                operation.getId(),
                membership.getAccountHolderId(),
                journey == null ? "NONE" : journey.getStatus(),
                journey != null && journey.isNetworkLinked());
        subscriptionPaymentFinalizationServiceProvider.getObject().finalizeConfirmedOperation(operation.getId());
    }

    private boolean requiresFinalizationReplay(Membership membership,
                                               MobileOnboardingJourney journey,
                                               Operation operation) {
        if (journey == null) {
            return false;
        }

        boolean missingPaymentConfirmation = Objects.equals(journey.getPaymentOperationId(), operation.getId())
                && journey.getStatus().ordinal() < MobileOnboardingJourneyStatus.PAYMENT_CONFIRMED.ordinal();
        boolean missingActiveMemberState = journey.getStatus().ordinal() < MobileOnboardingJourneyStatus.ACTIVE_MEMBER.ordinal();
        boolean sponsorSelected = journey.isSponsorSet() && journey.getSponsorAccountHolderId() != null;
        boolean missingPlacement = sponsorSelected
                && mlmRelationshipRepository.countByDescendantIdAndLevelGreaterThan(membership.getAccountHolderId(), 0) == 0;

        return missingPaymentConfirmation || missingActiveMemberState || missingPlacement;
    }

    private String resolvePendingAction(OperationStatus operationStatus, MembershipStatus membershipStatus) {
        return switch (operationStatus) {
            case CONFIRMED -> MembershipStatus.ACTIVE == membershipStatus ? "FINALIZED" : "FINALIZE_ATTEMPTED";
            case FAILED, EXPIRED, CANCELED -> MembershipStatus.FAILED == membershipStatus ? "MARKED_FAILED" : "FAILURE_ATTEMPTED";
            default -> "WAITING";
        };
    }

    public record ReconciliationResult(
            UUID membershipId,
            UUID operationId,
            String previousStatus,
            String currentStatus,
            String action
    ) {
    }
}
