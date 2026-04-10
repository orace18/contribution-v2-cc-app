package com.ccapp.mobile.onboarding.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import com.ccapp.financial.access.service.MemberFinancialProfileService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.graph.MlmGraphSyncService;
import com.ccapp.mlm.service.MLMPlacementService;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.entity.MobilePlanSelection;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import com.ccapp.mobile.onboarding.domain.enums.MobilePlanSelectionStatus;
import com.ccapp.mobile.onboarding.dto.ActivateFreePlanResponse;
import com.ccapp.mobile.onboarding.dto.MobileCountrySelectionResponse;
import com.ccapp.mobile.onboarding.dto.MobileOnboardingStatusResponse;
import com.ccapp.mobile.onboarding.dto.MobileSponsorSelectionResponse;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEvent;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEventType;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.mobile.onboarding.repository.MobilePlanSelectionRepository;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateResponse;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.client.JawuPayPspException;
import com.ccapp.payments.psp.service.JawuPayPaymentOrchestrationService;
import com.ccapp.payments.psp.service.JawuPayWebhookService;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.payments.subscription.service.SubscriptionService;
import com.ccapp.wallet.service.WalletLifecycleService;
import com.ccapp.wallet.service.WalletStatusSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileOnboardingJourneyService {

    private static final String DUPLICATE_IN_PROGRESS_MESSAGE =
            "A payment operation for this subscription is already in progress.";
    private static final String IDEMPOTENT_REUSE_MESSAGE =
            "This payment request was already processed.";
    private static final String ALREADY_CONFIRMED_MESSAGE =
            "This subscription payment is already confirmed.";

    private final MobileOnboardingJourneyRepository journeyRepository;
    private final MobilePlanSelectionRepository mobilePlanSelectionRepository;
    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionService subscriptionService;
    private final OperationService operationService;
    private final OperationRepository operationRepository;
    private final MembershipService membershipService;
    private final JawuPayPaymentOrchestrationService jawuPayPaymentOrchestrationService;
    private final ObjectProvider<JawuPayWebhookService> jawuPayWebhookServiceProvider;
    private final MLMPlacementService mlmPlacementService;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;
    private final MlmGraphSyncService mlmGraphSyncService;
    private final DomainEventPublisher domainEventPublisher;
    private final CountryContextService countryContextService;
    private final MemberFinancialProfileService memberFinancialProfileService;
    private final WalletLifecycleService walletLifecycleService;

    @Transactional
    public MobileOnboardingJourney markAuthenticated(UUID accountHolderId) {
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED)) {
            publish(accountHolderId, MobileOnboardingEventType.USER_AUTHENTICATED, Map.of());
        }
        return journeyRepository.save(journey);
    }

    @Transactional
    public MobileOnboardingJourney markWalletActivated(UUID accountHolderId) {
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.PHONE_VERIFIED)) {
            publish(accountHolderId, MobileOnboardingEventType.PHONE_VERIFIED, Map.of());
        }
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.WALLET_ACTIVATED)) {
            publish(accountHolderId, MobileOnboardingEventType.WALLET_ACTIVATED, Map.of());
        }
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED)) {
            journey.setOnboardingCompleted(true);
            publish(accountHolderId, MobileOnboardingEventType.ONBOARDING_COMPLETED, Map.of());
        }
        return journeyRepository.save(journey);
    }

    @Transactional
    public MobileOnboardingJourney onPhoneVerified(UUID accountHolderId, String phoneNumber) {
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);

        if (promoteStatus(journey, MobileOnboardingJourneyStatus.PHONE_VERIFIED)) {
            publish(accountHolderId, MobileOnboardingEventType.PHONE_VERIFIED, Map.of());
        }
        activateWalletIfEligible(journey, normalizeOptional(phoneNumber), accountHolderId);
        return journeyRepository.save(journey);
    }

    @Transactional
    public MobileCountrySelectionResponse selectCountry(UUID actorUserId,
                                                        UUID accountHolderId,
                                                        String requestedCountryCode) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);
        requireAtLeast(journey, MobileOnboardingJourneyStatus.AUTHENTICATED, "User must be authenticated first");

        AccountHolder accountHolder = accountHolderService.selectCountry(accountHolderId, normalizeCountryCode(requestedCountryCode));
        boolean changed = journey.getCountryCode() == null
                || !journey.getCountryCode().equalsIgnoreCase(accountHolder.getCountryCode());
        journey.setCountryCode(accountHolder.getCountryCode());
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.COUNTRY_SELECTED) || changed) {
            publish(accountHolderId, MobileOnboardingEventType.COUNTRY_SELECTED, Map.of(
                    "countryCode", accountHolder.getCountryCode()
            ));
        }
        journeyRepository.save(journey);
        return new MobileCountrySelectionResponse(accountHolderId, accountHolder.getCountryCode());
    }

    @Transactional
    public MobileSponsorSelectionResponse setSponsor(UUID actorUserId,
                                                     UUID accountHolderId,
                                                     UUID sponsorAccountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);
        requireAtLeast(journey, MobileOnboardingJourneyStatus.COUNTRY_SELECTED, "Country must be selected first");

        if (sponsorAccountHolderId != null) {
            if (accountHolderId.equals(sponsorAccountHolderId)) {
                throw new BusinessException("Account holder cannot sponsor itself");
            }
            accountHolderService.getById(sponsorAccountHolderId);
        }

        boolean changed = !journey.isSponsorSet()
                || !java.util.Objects.equals(journey.getSponsorAccountHolderId(), sponsorAccountHolderId);
        journey.setSponsorSet(true);
        journey.setSponsorAccountHolderId(sponsorAccountHolderId);
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.SPONSOR_SET) || changed) {
            publish(accountHolderId, MobileOnboardingEventType.SPONSOR_SELECTED, Map.of(
                    "sponsorAccountHolderId", sponsorAccountHolderId == null ? "NONE" : sponsorAccountHolderId.toString()
            ));
        }
        journeyRepository.save(journey);
        return new MobileSponsorSelectionResponse(accountHolderId, sponsorAccountHolderId, true);
    }

    @Transactional
    public void onPlanSelected(UUID accountHolderId, SubscriptionPlan selectedPlan) {
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);
        requireAtLeast(journey, MobileOnboardingJourneyStatus.COUNTRY_SELECTED, "Country must be selected first");

        boolean changed = !java.util.Objects.equals(journey.getPlanId(), selectedPlan.getId());
        journey.setPlanId(selectedPlan.getId());
        journey.setPlanCode(selectedPlan.getCode());
        journey.setPaymentOperationId(null);
        journey.setPaymentReference(null);
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.PLAN_SELECTED) || changed) {
            publish(accountHolderId, MobileOnboardingEventType.PLAN_SELECTED, Map.of(
                    "planId", selectedPlan.getId().toString(),
                    "planCode", selectedPlan.getCode()
            ));
        }
        journeyRepository.save(journey);
    }

    @Transactional
    public ActivateFreePlanResponse activateFreePlan(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);
        requireAtLeast(journey, MobileOnboardingJourneyStatus.PLAN_SELECTED, "Plan must be selected first");

        SubscriptionPlan plan = resolveSelectedPlan(journey, accountHolderId);
        if (!isFreePlan(plan)) {
            throw new BusinessException("Selected plan is not a FREE plan");
        }

        String idempotencyKey = "mobile-free-sub:" + accountHolderId + ":" + plan.getId();
        Operation operation = operationRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> operationRepository.save(Operation.builder()
                        .accountHolderId(accountHolderId)
                        .actorUserId(actorUserId)
                        .type(OperationType.SUBSCRIPTION)
                        .status(OperationStatus.CONFIRMED)
                        .amount(BigDecimal.ZERO.setScale(4))
                        .currency(plan.getCurrency().trim().toUpperCase(Locale.ROOT))
                        .idempotencyKey(idempotencyKey)
                        .planId(plan.getId())
                        .description("Mobile free onboarding subscription " + plan.getCode())
                        .build()));

        Membership membership = membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);
        Membership activeMembership = membership.getStatus() == MembershipStatus.ACTIVE
                ? membership
                : membershipService.activateForConfirmedOperation(operation);

        transitionAfterMembershipActivated(journey, actorUserId, operation, true);
        journeyRepository.save(journey);

        return new ActivateFreePlanResponse(
                operation.getId(),
                activeMembership.getStatus().name(),
                journey.getStatus().name(),
                memberFinancialProfileService.getProfile(accountHolderId).membershipActive()
        );
    }

    @Transactional(noRollbackFor = JawuPayPspException.class)
    public MobilePaymentInitiateResponse initiatePaidPlanPayment(UUID actorUserId,
                                                                 UUID accountHolderId,
                                                                 String requestedIdempotencyKey,
                                                                 String requestedCountryCode,
                                                                 String paymentMethodId,
                                                                 String msisdn,
                                                                 String operatorHint) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);
        requireAtLeast(journey, MobileOnboardingJourneyStatus.PLAN_SELECTED, "Plan must be selected first");

        SubscriptionPlan plan = resolveSelectedPlan(journey, accountHolderId);
        if (isFreePlan(plan)) {
            throw new BusinessException("Use activate-free for FREE plan");
        }

        String paymentCountry = normalizeRequiredCountryCode(requestedCountryCode);
        String normalizedPaymentMethodId = normalizeRequiredPaymentMethodId(paymentMethodId);
        String normalizedMsisdn = normalizeMsisdn(msisdn, normalizedPaymentMethodId);
        String normalizedOperatorHint = normalizeOptional(operatorHint);

        if (!paymentCountry.equalsIgnoreCase(plan.getCountry())) {
            throw new BusinessException("Selected plan is not available for requested payment country");
        }

        Operation existingIdempotentOperation = findExistingSubscriptionOperationByIdempotencyKey(
                requestedIdempotencyKey,
                accountHolderId,
                actorUserId,
                plan
        ).orElse(null);
        if (existingIdempotentOperation != null) {
            log.info("payment_duplicate_detected operationId={} accountHolderId={} operationType={} planId={} status={}",
                    existingIdempotentOperation.getId(),
                    accountHolderId,
                    OperationType.SUBSCRIPTION,
                    plan.getId(),
                    existingIdempotentOperation.getStatus());
            Operation resolvedOperation = recoverExistingSubscriptionOperation(existingIdempotentOperation, accountHolderId, plan.getId());
            return buildDuplicatePaymentResponse(
                    journey,
                    accountHolderId,
                    plan,
                    resolvedOperation,
                    IDEMPOTENT_REUSE_MESSAGE
            );
        }

        Operation reusableOperation = findReusablePendingSubscriptionOperation(accountHolderId, plan.getId()).orElse(null);
        if (reusableOperation != null) {
            log.info("payment_duplicate_detected operationId={} accountHolderId={} operationType={} planId={} status={}",
                    reusableOperation.getId(),
                    accountHolderId,
                    OperationType.SUBSCRIPTION,
                    plan.getId(),
                    reusableOperation.getStatus());
            Operation resolvedOperation = recoverExistingSubscriptionOperation(reusableOperation, accountHolderId, plan.getId());
            if (isSubscriptionOperationInFlight(resolvedOperation.getStatus()) || resolvedOperation.getStatus() == OperationStatus.CONFIRMED) {
                return buildDuplicatePaymentResponse(
                        journey,
                        accountHolderId,
                        plan,
                        resolvedOperation,
                        DUPLICATE_IN_PROGRESS_MESSAGE
                );
            }
        }

        String idempotencyKey = resolveMobilePaymentIdempotencyKey(
                requestedIdempotencyKey,
                accountHolderId,
                plan.getId()
        );
        String description = "Mobile paid onboarding subscription " + plan.getCode();
        JawuPayPaymentOrchestrationService.InitiatePaymentResult orchestrationResult =
                jawuPayPaymentOrchestrationService.initiatePayment(
                        accountHolderId,
                        actorUserId,
                        plan.getPrice(),
                        plan.getCurrency(),
                        paymentCountry,
                        normalizedPaymentMethodId,
                        normalizedMsisdn,
                        normalizedOperatorHint,
                        idempotencyKey,
                        description,
                        OperationType.SUBSCRIPTION,
                        plan.getId()
                );
        Operation paymentOperation = operationService.getById(orchestrationResult.operationId());
        paymentOperation.setPlanId(plan.getId());
        paymentOperation = operationRepository.save(paymentOperation);

        membershipService.createPendingMembership(paymentOperation.getId(), accountHolderId, plan);

        if (paymentOperation.getStatus() == OperationStatus.CONFIRMED) {
            transitionAfterMembershipActivated(journey, actorUserId, paymentOperation, false);
            journeyRepository.save(journey);
            return new MobilePaymentInitiateResponse(
                    paymentOperation.getId(),
                    paymentOperation.getStatus().name(),
                    paymentOperation.getPspReference(),
                    paymentOperation.getRedirectUrl()
            );
        }

        updatePaymentPending(journey, paymentOperation.getId(), paymentOperation.getPspReference(), false);
        journeyRepository.save(journey);

        return new MobilePaymentInitiateResponse(
                paymentOperation.getId(),
                paymentOperation.getStatus().name(),
                paymentOperation.getPspReference(),
                paymentOperation.getRedirectUrl() != null
                        ? paymentOperation.getRedirectUrl()
                        : orchestrationResult.redirectUrl()
        );
    }

    @Transactional
    public void onPaidPaymentConfirmed(Operation operation) {
        if (operation.getType() != OperationType.SUBSCRIPTION) {
            return;
        }

        MobileOnboardingJourney journey = journeyRepository.findByAccountHolderId(operation.getAccountHolderId())
                .orElse(null);
        if (journey == null) {
            return;
        }

        transitionAfterMembershipActivated(journey, operation.getActorUserId(), operation, false);
        journeyRepository.save(journey);
    }

    @Transactional
    public void onPaidPaymentFailed(Operation operation) {
        if (operation.getType() != OperationType.SUBSCRIPTION) {
            return;
        }
        MobileOnboardingJourney journey = journeyRepository.findByAccountHolderId(operation.getAccountHolderId())
                .orElse(null);
        if (journey == null) {
            return;
        }

        if (journey.getStatus() == MobileOnboardingJourneyStatus.PAYMENT_PENDING) {
            journey.setStatus(MobileOnboardingJourneyStatus.PLAN_SELECTED);
        }
        if (java.util.Objects.equals(journey.getPaymentOperationId(), operation.getId())) {
            journey.setPaymentOperationId(null);
            journey.setPaymentReference(null);
        }
        journeyRepository.save(journey);
    }

    @Transactional(readOnly = true)
    public MobileOnboardingStatusResponse getStatus(UUID accountHolderId) {
        MemberFinancialProfile financialProfile = memberFinancialProfileService.getProfile(accountHolderId);
        WalletStatusSnapshot walletStatus = walletLifecycleService.resolveStatus(accountHolderId);
        MobileOnboardingJourney journey = resolveDerivedJourneySnapshot(accountHolderId, financialProfile, walletStatus);
        boolean active = financialProfile.membershipActive();
        boolean networkReady = journey.isNetworkLinked() && mlmGraphSyncService.isNetworkReady(accountHolderId);
        return new MobileOnboardingStatusResponse(
                journey.getStatus().name(),
                completedSteps(journey, walletStatus),
                nextStep(journey, active, walletStatus),
                null,
                active,
                networkReady,
                journey.getPaymentOperationId(),
                journey.getPaymentReference()
        );
    }

    @Transactional
    public MobileOnboardingJourney ensureJourney(UUID accountHolderId) {
        return getOrCreate(accountHolderId);
    }

    private MobileOnboardingJourney resolveDerivedJourneySnapshot(UUID accountHolderId,
                                                                  MemberFinancialProfile financialProfile,
                                                                  WalletStatusSnapshot walletStatus) {
        MobileOnboardingJourney base = journeyRepository.findByAccountHolderId(accountHolderId)
                .orElseGet(() -> MobileOnboardingJourney.builder()
                        .accountHolderId(accountHolderId)
                        .status(MobileOnboardingJourneyStatus.REGISTERED)
                        .build());

        MobileOnboardingJourney copy = copyOf(base);
        promoteStatus(copy, MobileOnboardingJourneyStatus.AUTHENTICATED);

        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        if (accountHolder.getCountryCode() != null && !accountHolder.getCountryCode().isBlank()) {
            copy.setCountryCode(accountHolder.getCountryCode());
            promoteStatus(copy, MobileOnboardingJourneyStatus.COUNTRY_SELECTED);
        }

        if (copy.isSponsorSet()) {
            promoteStatus(copy, MobileOnboardingJourneyStatus.SPONSOR_SET);
        }

        MobilePlanSelection planSelection = mobilePlanSelectionRepository.findByAccountHolderId(accountHolderId).orElse(null);
        if (planSelection != null) {
            copy.setPlanId(planSelection.getPlanId());
            copy.setPlanCode(planSelection.getPlanCode());
            promoteStatus(copy, MobileOnboardingJourneyStatus.PLAN_SELECTED);
            if (planSelection.getStatus() == MobilePlanSelectionStatus.PENDING_PAYMENT
                    && copy.getStatus().ordinal() < MobileOnboardingJourneyStatus.PAYMENT_CONFIRMED.ordinal()) {
                promoteStatus(copy, MobileOnboardingJourneyStatus.PAYMENT_PENDING);
            }
        }

        if (copy.getPaymentOperationId() != null) {
            Operation paymentOperation = operationRepository.findById(copy.getPaymentOperationId()).orElse(null);
            if (paymentOperation != null && paymentOperation.getStatus() == OperationStatus.CONFIRMED) {
                promoteStatus(copy, MobileOnboardingJourneyStatus.PAYMENT_CONFIRMED);
            }
        }

        if (financialProfile.membershipActive()) {
            promoteStatus(copy, MobileOnboardingJourneyStatus.ACTIVE_MEMBER);
        }

        if (walletStatus.phoneVerified()
                && copy.getStatus().ordinal() >= MobileOnboardingJourneyStatus.ACTIVE_MEMBER.ordinal()) {
            promoteStatus(copy, MobileOnboardingJourneyStatus.PHONE_VERIFIED);
        }

        if (walletStatus.active()) {
            promoteStatus(copy, MobileOnboardingJourneyStatus.WALLET_ACTIVATED);
        }

        if (mlmRelationshipRepository.existsByDescendantId(accountHolderId) || copy.isNetworkLinked()) {
            copy.setNetworkLinked(true);
        }

        if (copy.isOnboardingCompleted()
                || copy.getStatus().ordinal() >= MobileOnboardingJourneyStatus.WALLET_ACTIVATED.ordinal()) {
            copy.setOnboardingCompleted(true);
            promoteStatus(copy, MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED);
        }
        return copy;
    }

    private void transitionAfterMembershipActivated(MobileOnboardingJourney journey,
                                                    UUID actorUserId,
                                                    Operation operation,
                                                    boolean skipPaymentEvent) {
        if (!skipPaymentEvent) {
            if (promoteStatus(journey, MobileOnboardingJourneyStatus.PAYMENT_CONFIRMED)) {
                publish(journey.getAccountHolderId(), MobileOnboardingEventType.PAYMENT_CONFIRMED, Map.of(
                        "operationId", operation.getId().toString(),
                        "pspReference", operation.getPspReference() == null ? "" : operation.getPspReference()
                ));
            }
        }

        if (promoteStatus(journey, MobileOnboardingJourneyStatus.ACTIVE_MEMBER)) {
            publish(journey.getAccountHolderId(), MobileOnboardingEventType.MEMBERSHIP_ACTIVATED, Map.of(
                    "operationId", operation.getId().toString()
            ));
        }

        syncNetworkLink(journey, actorUserId);
        activateWalletIfEligible(journey, null, actorUserId);
    }

    private void syncNetworkLink(MobileOnboardingJourney journey, UUID actorUserId) {
        boolean alreadyLinked = journey.isNetworkLinked();
        ensureNetworkLinked(journey, actorUserId == null ? journey.getAccountHolderId() : actorUserId);
        if (journey.isNetworkLinked() && !alreadyLinked) {
            publish(journey.getAccountHolderId(), MobileOnboardingEventType.NETWORK_LINKED, Map.of());
        }
    }

    private void ensureNetworkLinked(MobileOnboardingJourney journey, UUID actorUserId) {
        UUID accountHolderId = journey.getAccountHolderId();
        if (mlmRelationshipRepository.existsByDescendantId(accountHolderId)) {
            ensureRootLinkIfMissing(accountHolderId);
            journey.setNetworkLinked(true);
            return;
        }

        UUID sponsorAccountHolderId = journey.getSponsorAccountHolderId();
        if (sponsorAccountHolderId != null) {
            try {
                mlmPlacementService.placeAccountHolder(actorUserId, accountHolderId, sponsorAccountHolderId);
            } catch (ConflictException ex) {
                if (mlmRelationshipRepository.countByDescendantIdAndLevelGreaterThan(accountHolderId, 0) == 0) {
                    throw ex;
                }
            }
            journey.setNetworkLinked(mlmRelationshipRepository.existsByDescendantId(accountHolderId));
            return;
        }

        if (!mlmRelationshipRepository.existsByAncestorIdAndDescendantId(accountHolderId, accountHolderId)) {
            mlmRelationshipRepository.save(MlmRelationship.builder()
                    .ancestorId(accountHolderId)
                    .descendantId(accountHolderId)
                    .level(0)
                    .createdAt(OffsetDateTime.now())
                    .build());
        }
        ensureRootLinkIfMissing(accountHolderId);
        journey.setNetworkLinked(mlmRelationshipRepository.existsByDescendantId(accountHolderId));
    }

    private void ensureRootLinkIfMissing(UUID accountHolderId) {
        if (mlmMemberLinkRepository.existsById(accountHolderId)) {
            return;
        }
        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(accountHolderId)
                .sponsorAccountHolderId(null)
                .placementAccountHolderId(accountHolderId)
                .build());
    }

    private void updatePaymentPending(MobileOnboardingJourney journey,
                                      UUID operationId,
                                      String pspReference,
                                      boolean alreadyPending) {
        journey.setPaymentOperationId(operationId);
        journey.setPaymentReference(pspReference);
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.PAYMENT_PENDING) || !alreadyPending) {
            publish(journey.getAccountHolderId(), MobileOnboardingEventType.PAYMENT_INITIATED, Map.of(
                    "operationId", operationId.toString(),
                    "pspReference", pspReference == null ? "" : pspReference
            ));
        }
    }

    private void activateWalletIfEligible(MobileOnboardingJourney journey,
                                          String verifiedPhoneNumber,
                                          UUID actorUserId) {
        String normalizedPhone = normalizeOptional(verifiedPhoneNumber);
        if (normalizedPhone != null
                && journey.getStatus().ordinal() < MobileOnboardingJourneyStatus.PHONE_VERIFIED.ordinal()
                && promoteStatus(journey, MobileOnboardingJourneyStatus.PHONE_VERIFIED)) {
                publish(journey.getAccountHolderId(), MobileOnboardingEventType.PHONE_VERIFIED, Map.of());
        }

        if (walletLifecycleService.activateIfEligible(journey.getAccountHolderId(), normalizedPhone).isEmpty()) {
            return;
        }

        if (normalizedPhone == null
                && journey.getStatus().ordinal() < MobileOnboardingJourneyStatus.PHONE_VERIFIED.ordinal()
                && promoteStatus(journey, MobileOnboardingJourneyStatus.PHONE_VERIFIED)) {
            publish(journey.getAccountHolderId(), MobileOnboardingEventType.PHONE_VERIFIED, Map.of());
        }

        if (promoteStatus(journey, MobileOnboardingJourneyStatus.WALLET_ACTIVATED)) {
            publish(journey.getAccountHolderId(), MobileOnboardingEventType.WALLET_ACTIVATED, Map.of());
        }

        finalizeOnboarding(journey, actorUserId);
    }

    private void finalizeOnboarding(MobileOnboardingJourney journey, UUID actorUserId) {
        if (journey.getStatus().ordinal() < MobileOnboardingJourneyStatus.WALLET_ACTIVATED.ordinal()) {
            return;
        }
        syncNetworkLink(journey, actorUserId);
        journey.setOnboardingCompleted(true);
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED)) {
            publish(journey.getAccountHolderId(), MobileOnboardingEventType.ONBOARDING_COMPLETED, Map.of());
        }
    }

    private SubscriptionPlan resolveSelectedPlan(MobileOnboardingJourney journey, UUID accountHolderId) {
        UUID planId = journey.getPlanId();
        if (planId == null) {
            planId = mobilePlanSelectionRepository.findByAccountHolderId(accountHolderId)
                    .map(MobilePlanSelection::getPlanId)
                    .orElse(null);
        }
        if (planId == null) {
            throw new BusinessException("No selected plan found for onboarding");
        }
        SubscriptionPlan plan = subscriptionPlanRepository.findByIdAndActiveTrue(planId)
                .orElseThrow(() -> new BusinessException("Selected plan is not active"));

        CountryContext countryContext = countryContextService.resolve(accountHolderId);
        String catalogCountryCode = subscriptionService.resolveCatalogCountryCode(countryContext.serviceCountryCode());
        if (!catalogCountryCode.equalsIgnoreCase(plan.getCountry())) {
            throw new BusinessException("Selected plan is not available for the requested country catalog");
        }
        return plan;
    }

    private java.util.Optional<Operation> findReusablePendingSubscriptionOperation(UUID accountHolderId, UUID planId) {
        return operationRepository.findFirstByAccountHolderIdAndTypeAndPlanIdAndStatusInOrderByCreatedAtDesc(
                accountHolderId,
                OperationType.SUBSCRIPTION,
                planId,
                subscriptionInFlightStatuses()
        );
    }

    private Optional<Operation> findExistingSubscriptionOperationByIdempotencyKey(String requestedIdempotencyKey,
                                                                                   UUID accountHolderId,
                                                                                   UUID actorUserId,
                                                                                   SubscriptionPlan plan) {
        String normalizedRequestedKey = normalizeOptional(requestedIdempotencyKey);
        if (normalizedRequestedKey == null) {
            return Optional.empty();
        }
        return operationService.findByIdempotencyKey(normalizedRequestedKey)
                .map(existing -> validateExistingSubscriptionOperation(existing, accountHolderId, actorUserId, plan));
    }

    private Operation validateExistingSubscriptionOperation(Operation operation,
                                                            UUID accountHolderId,
                                                            UUID actorUserId,
                                                            SubscriptionPlan plan) {
        if (operation.getType() != OperationType.SUBSCRIPTION) {
            throw new ConflictException("Idempotency key already used by another operation type");
        }
        if (!accountHolderId.equals(operation.getAccountHolderId())) {
            throw new ConflictException("Idempotency key already used by another account holder");
        }
        if (operation.getActorUserId() != null && !actorUserId.equals(operation.getActorUserId())) {
            throw new ConflictException("Idempotency key already used by another actor");
        }
        if (operation.getAmount() == null || operation.getAmount().compareTo(plan.getPrice()) != 0) {
            throw new ConflictException("Idempotency key already used by another payment amount");
        }
        if (operation.getCurrency() == null || !operation.getCurrency().equalsIgnoreCase(plan.getCurrency())) {
            throw new ConflictException("Idempotency key already used by another payment currency");
        }
        if (operation.getPlanId() == null) {
            operation.setPlanId(plan.getId());
            return operationRepository.save(operation);
        }
        if (!plan.getId().equals(operation.getPlanId())) {
            throw new ConflictException("Idempotency key already used by another subscription target");
        }
        return operation;
    }

    private Operation recoverExistingSubscriptionOperation(Operation operation, UUID accountHolderId, UUID planId) {
        Operation currentOperation = stampPlanIdIfMissing(operation, planId);
        log.info("payment_recovery_attempt operationId={} accountHolderId={} operationType={} planId={} status={}",
                currentOperation.getId(),
                accountHolderId,
                OperationType.SUBSCRIPTION,
                planId,
                currentOperation.getStatus());

        if (!isSubscriptionOperationInFlight(currentOperation.getStatus())) {
            log.info("payment_recovery_result operationId={} accountHolderId={} operationType={} planId={} status={} action=already_terminal",
                    currentOperation.getId(),
                    accountHolderId,
                    OperationType.SUBSCRIPTION,
                    planId,
                    currentOperation.getStatus());
            return currentOperation;
        }

        String paymentLookupReference = resolvePaymentLookupReference(currentOperation);
        if (paymentLookupReference == null) {
            log.info("payment_recovery_result operationId={} accountHolderId={} operationType={} planId={} status={} action=skipped_no_lookup_reference",
                    currentOperation.getId(),
                    accountHolderId,
                    OperationType.SUBSCRIPTION,
                    planId,
                    currentOperation.getStatus());
            return currentOperation;
        }

        try {
            jawuPayWebhookServiceProvider.getObject().checkAndApplyPaymentStatus(paymentLookupReference);
        } catch (Exception ex) {
            log.warn("payment_recovery_result operationId={} accountHolderId={} operationType={} planId={} status={} action=recovery_failed message={}",
                    currentOperation.getId(),
                    accountHolderId,
                    OperationType.SUBSCRIPTION,
                    planId,
                    currentOperation.getStatus(),
                    normalizeOptional(ex.getMessage()),
                    ex);
            return currentOperation;
        }

        Operation refreshedOperation = stampPlanIdIfMissing(operationService.getById(currentOperation.getId()), planId);
        log.info("payment_recovery_result operationId={} accountHolderId={} operationType={} planId={} status={} action=reconciled",
                refreshedOperation.getId(),
                accountHolderId,
                OperationType.SUBSCRIPTION,
                planId,
                refreshedOperation.getStatus());
        return refreshedOperation;
    }

    private Operation stampPlanIdIfMissing(Operation operation, UUID planId) {
        if (operation == null || planId == null || operation.getPlanId() != null) {
            return operation;
        }
        operation.setPlanId(planId);
        return operationRepository.save(operation);
    }

    private MobilePaymentInitiateResponse buildDuplicatePaymentResponse(MobileOnboardingJourney journey,
                                                                        UUID accountHolderId,
                                                                        SubscriptionPlan plan,
                                                                        Operation operation,
                                                                        String message) {
        if (operation != null && isSubscriptionOperationInFlight(operation.getStatus())) {
            membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);
            updatePaymentPending(journey, operation.getId(), operation.getPspReference(), true);
            journeyRepository.save(journey);
        }
        log.info("existing_operation_reused operationId={} accountHolderId={} operationType={} planId={} status={}",
                operation == null ? null : operation.getId(),
                accountHolderId,
                OperationType.SUBSCRIPTION,
                plan.getId(),
                operation == null ? null : operation.getStatus());
        String responseMessage = operation != null && operation.getStatus() == OperationStatus.CONFIRMED
                ? ALREADY_CONFIRMED_MESSAGE
                : message;
        return new MobilePaymentInitiateResponse(
                operation == null ? null : operation.getId(),
                operation == null || operation.getStatus() == null ? null : operation.getStatus().name(),
                operation == null ? null : operation.getPspReference(),
                operation == null ? null : operation.getRedirectUrl(),
                operation == null ? null : operation.getId(),
                operation == null || operation.getStatus() == null ? null : operation.getStatus().name(),
                true,
                responseMessage
        );
    }

    private EnumSet<OperationStatus> subscriptionInFlightStatuses() {
        return EnumSet.complementOf(EnumSet.of(
                OperationStatus.CONFIRMED,
                OperationStatus.FAILED,
                OperationStatus.EXPIRED,
                OperationStatus.CANCELED,
                OperationStatus.PAYOUT_INITIATED
        ));
    }

    private boolean isSubscriptionOperationInFlight(OperationStatus status) {
        return status != null && subscriptionInFlightStatuses().contains(status);
    }

    private String resolvePaymentLookupReference(Operation operation) {
        if (operation == null) {
            return null;
        }
        String paymentId = normalizeOptional(operation.getPaymentId());
        if (paymentId != null) {
            return paymentId;
        }
        String providerReference = normalizeOptional(operation.getProviderReference());
        if (providerReference != null) {
            return providerReference;
        }
        return normalizeOptional(operation.getPspReference());
    }

    private String generateMobilePaymentIdempotencyKey(UUID accountHolderId, UUID planId) {
        return "mobile-paid-sub:" + accountHolderId + ":" + planId + ":" + UUID.randomUUID();
    }

    private String resolveMobilePaymentIdempotencyKey(String requestedIdempotencyKey,
                                                      UUID accountHolderId,
                                                      UUID planId) {
        String normalizedRequestedKey = normalizeOptional(requestedIdempotencyKey);
        return normalizedRequestedKey != null
                ? normalizedRequestedKey
                : generateMobilePaymentIdempotencyKey(accountHolderId, planId);
    }

    private boolean isFreePlan(SubscriptionPlan plan) {
        return plan.getGrade() == SubscriptionGrade.FREE
                || (plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) == 0);
    }

    private String normalizeRequiredCountryCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Payment country is required");
        }
        String normalized = normalizeCountryCode(value);
        if (normalized.length() < 2 || normalized.length() > 3) {
            throw new BusinessException("Payment country must be ISO code length 2 or 3");
        }
        return normalized;
    }

    private String normalizeRequiredPaymentMethodId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Payment method is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMsisdn(String value, String paymentMethodId) {
        String normalized = normalizeOptional(value);
        if (isMsisdnRequired(paymentMethodId) && normalized == null) {
            throw new BusinessException("MSISDN is required for payment method " + paymentMethodId);
        }
        return normalized;
    }

    private boolean isMsisdnRequired(String paymentMethodId) {
        return paymentMethodId != null
                && (paymentMethodId.startsWith("MOBILE_MONEY")
                || paymentMethodId.contains("MOMO"));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCountryCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireAtLeast(MobileOnboardingJourney journey,
                                MobileOnboardingJourneyStatus minimum,
                                String message) {
        if (journey.getStatus().ordinal() < minimum.ordinal()) {
            throw new BusinessException(message);
        }
    }

    private boolean promoteStatus(MobileOnboardingJourney journey, MobileOnboardingJourneyStatus target) {
        if (journey.getStatus() == null || journey.getStatus().ordinal() < target.ordinal()) {
            journey.setStatus(target);
            return true;
        }
        return false;
    }

    private MobileOnboardingJourney getOrCreate(UUID accountHolderId) {
        return journeyRepository.findByAccountHolderId(accountHolderId)
                .orElseGet(() -> journeyRepository.save(MobileOnboardingJourney.builder()
                        .accountHolderId(accountHolderId)
                        .status(MobileOnboardingJourneyStatus.REGISTERED)
                        .sponsorSet(false)
                        .networkLinked(false)
                        .onboardingCompleted(false)
                        .build()));
    }

    private List<String> completedSteps(MobileOnboardingJourney journey,
                                        WalletStatusSnapshot walletStatus) {
        List<String> steps = new ArrayList<>();
        steps.add(MobileOnboardingJourneyStatus.REGISTERED.name());
        if (journey.getStatus().ordinal() >= MobileOnboardingJourneyStatus.AUTHENTICATED.ordinal()) {
            steps.add(MobileOnboardingJourneyStatus.AUTHENTICATED.name());
        }
        if (journey.getCountryCode() != null && !journey.getCountryCode().isBlank()) {
            steps.add(MobileOnboardingJourneyStatus.COUNTRY_SELECTED.name());
        }
        if (journey.isSponsorSet()) {
            steps.add(MobileOnboardingJourneyStatus.SPONSOR_SET.name());
        }
        if (journey.getPlanId() != null) {
            steps.add(MobileOnboardingJourneyStatus.PLAN_SELECTED.name());
        }
        if (journey.getStatus().ordinal() >= MobileOnboardingJourneyStatus.PAYMENT_PENDING.ordinal()
                && journey.getPaymentOperationId() != null) {
            steps.add(MobileOnboardingJourneyStatus.PAYMENT_PENDING.name());
        }
        if (journey.getStatus().ordinal() >= MobileOnboardingJourneyStatus.PAYMENT_CONFIRMED.ordinal()) {
            steps.add(MobileOnboardingJourneyStatus.PAYMENT_CONFIRMED.name());
        }
        if (journey.getStatus().ordinal() >= MobileOnboardingJourneyStatus.ACTIVE_MEMBER.ordinal()) {
            steps.add(MobileOnboardingJourneyStatus.ACTIVE_MEMBER.name());
        }
        if (walletStatus.phoneVerified()
                || journey.getStatus().ordinal() >= MobileOnboardingJourneyStatus.PHONE_VERIFIED.ordinal()) {
            steps.add(MobileOnboardingJourneyStatus.PHONE_VERIFIED.name());
        }
        if (journey.getStatus().ordinal() >= MobileOnboardingJourneyStatus.WALLET_ACTIVATED.ordinal()) {
            steps.add(MobileOnboardingJourneyStatus.WALLET_ACTIVATED.name());
        }
        if (journey.isOnboardingCompleted()
                || journey.getStatus().ordinal() >= MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED.ordinal()) {
            steps.add(MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED.name());
        }
        if (journey.isNetworkLinked()) {
            steps.add(MobileOnboardingJourneyStatus.NETWORK_LINKED.name());
        }
        return List.copyOf(steps);
    }

    private MobileOnboardingStatusResponse.NextStep nextStep(MobileOnboardingJourney journey,
                                                             boolean activeMembership,
                                                             WalletStatusSnapshot walletStatus) {
        if (journey.getStatus() == MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED) {
            return null;
        }
        return switch (journey.getStatus()) {
            case REGISTERED, AUTHENTICATED -> new MobileOnboardingStatusResponse.NextStep(
                    "SELECT_COUNTRY",
                    "POST",
                    "/api/mobile/onboarding/select-country",
                    "Select your country to continue"
            );
            case COUNTRY_SELECTED, SPONSOR_SET -> new MobileOnboardingStatusResponse.NextStep(
                    "SELECT_PLAN",
                    "POST",
                    "/api/mobile/onboarding/select-plan",
                    "Select a subscription plan"
            );
            case PLAN_SELECTED -> {
                SubscriptionPlan plan = journey.getPlanId() == null
                        ? null
                        : subscriptionPlanRepository.findByIdAndActiveTrue(journey.getPlanId()).orElse(null);
                if (plan != null && isFreePlan(plan)) {
                    yield new MobileOnboardingStatusResponse.NextStep(
                            "ACTIVATE_FREE",
                            "POST",
                            "/api/mobile/onboarding/activate-free",
                            "Activate the selected free plan"
                    );
                }
                yield new MobileOnboardingStatusResponse.NextStep(
                        "INITIATE_PAYMENT",
                        "POST",
                        "/api/mobile/payments/initiate",
                        "Initiate paid plan payment"
                );
            }
            case PAYMENT_PENDING -> new MobileOnboardingStatusResponse.NextStep(
                    "WAIT_PAYMENT_CONFIRMATION",
                    "GET",
                    "/api/mobile/onboarding/status",
                    "Wait for PSP confirmation callback"
            );
            case PAYMENT_CONFIRMED, ACTIVE_MEMBER, PHONE_VERIFIED, WALLET_ACTIVATED -> walletActivationNextStep(walletStatus);
            case NETWORK_LINKED -> activeMembership
                    ? null
                    : new MobileOnboardingStatusResponse.NextStep(
                    "VERIFY_PHONE",
                    "POST",
                    "/api/mobile/phone-verifications/start",
                    "Verify your phone to complete onboarding"
            );
            case ONBOARDING_COMPLETED -> null;
        };
    }

    private MobileOnboardingStatusResponse.NextStep walletActivationNextStep(
            WalletStatusSnapshot walletStatus) {
        return switch (walletStatus.state()) {
            case VERIFY_PHONE -> new MobileOnboardingStatusResponse.NextStep(
                    "VERIFY_PHONE",
                    "POST",
                    "/api/mobile/phone-verifications/start",
                    walletStatus.message()
            );
            case WAIT_MEMBERSHIP_ACTIVATION -> new MobileOnboardingStatusResponse.NextStep(
                    "WAIT_MEMBERSHIP_ACTIVATION",
                    "GET",
                    "/api/mobile/onboarding/status",
                    walletStatus.message()
            );
            case WAIT_WALLET_ACTIVATION -> new MobileOnboardingStatusResponse.NextStep(
                    "WAIT_WALLET_ACTIVATION",
                    "GET",
                    "/wallet/status",
                    walletStatus.message()
            );
            case WALLET_ACTIVATION_BLOCKED -> new MobileOnboardingStatusResponse.NextStep(
                    "WALLET_ACTIVATION_BLOCKED",
                    "GET",
                    "/wallet/status",
                    walletStatus.message()
            );
            case WALLET_ACTIVE -> null;
        };
    }

    private MobileOnboardingJourney copyOf(MobileOnboardingJourney source) {
        return MobileOnboardingJourney.builder()
                .id(source.getId())
                .accountHolderId(source.getAccountHolderId())
                .status(source.getStatus())
                .countryCode(source.getCountryCode())
                .sponsorSet(source.isSponsorSet())
                .sponsorAccountHolderId(source.getSponsorAccountHolderId())
                .planId(source.getPlanId())
                .planCode(source.getPlanCode())
                .paymentOperationId(source.getPaymentOperationId())
                .paymentReference(source.getPaymentReference())
                .networkLinked(source.isNetworkLinked())
                .onboardingCompleted(source.isOnboardingCompleted())
                .build();
    }

    private void publish(UUID accountHolderId, MobileOnboardingEventType eventType, Map<String, String> metadata) {
        domainEventPublisher.publish(MobileOnboardingEvent.create(accountHolderId, eventType, metadata));
    }
}
