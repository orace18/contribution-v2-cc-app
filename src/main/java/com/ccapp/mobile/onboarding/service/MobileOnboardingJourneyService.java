package com.ccapp.mobile.onboarding.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
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
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import com.ccapp.payments.service.PaymentService;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.payments.subscription.service.SubscriptionService;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileOnboardingJourneyService {

    private final MobileOnboardingJourneyRepository journeyRepository;
    private final MobilePlanSelectionRepository mobilePlanSelectionRepository;
    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionService subscriptionService;
    private final OperationService operationService;
    private final OperationRepository operationRepository;
    private final MembershipService membershipService;
    private final PaymentService paymentService;
    private final MLMPlacementService mlmPlacementService;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;
    private final MlmGraphSyncService mlmGraphSyncService;
    private final DomainEventPublisher domainEventPublisher;
    private final CountryContextService countryContextService;

    @Transactional
    public MobileOnboardingJourney markAuthenticated(UUID accountHolderId) {
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        if (promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED)) {
            publish(accountHolderId, MobileOnboardingEventType.USER_AUTHENTICATED, Map.of());
        }
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
        requireAtLeast(journey, MobileOnboardingJourneyStatus.SPONSOR_SET, "Sponsor step must be completed first");

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
                isMembershipActive(activeMembership)
        );
    }

    @Transactional
    public MobilePaymentInitiateResponse initiatePaidPlanPayment(UUID actorUserId,
                                                                 UUID accountHolderId,
                                                                 String requestedIdempotencyKey) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        MobileOnboardingJourney journey = getOrCreate(accountHolderId);
        promoteStatus(journey, MobileOnboardingJourneyStatus.AUTHENTICATED);
        requireAtLeast(journey, MobileOnboardingJourneyStatus.PLAN_SELECTED, "Plan must be selected first");

        SubscriptionPlan plan = resolveSelectedPlan(journey, accountHolderId);
        if (isFreePlan(plan)) {
            throw new BusinessException("Use activate-free for FREE plan");
        }

        String idempotencyKey = (requestedIdempotencyKey == null || requestedIdempotencyKey.isBlank())
                ? "mobile-paid-sub:" + accountHolderId + ":" + plan.getId() + ":" + UUID.randomUUID()
                : requestedIdempotencyKey.trim();

        Operation operation = operationService.createInitiatedOperation(
                accountHolderId,
                actorUserId,
                OperationType.SUBSCRIPTION,
                plan.getPrice(),
                plan.getCurrency(),
                idempotencyKey,
                "Mobile paid onboarding subscription " + plan.getCode(),
                plan.getId()
        );

        membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);

        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            transitionAfterMembershipActivated(journey, actorUserId, operation, false);
            journeyRepository.save(journey);
            return new MobilePaymentInitiateResponse(
                    operation.getId(),
                    operation.getStatus().name(),
                    operation.getPspReference(),
                    null
            );
        }

        if (operation.getStatus() == OperationStatus.PENDING_PSP) {
            updatePaymentPending(journey, operation.getId(), operation.getPspReference(), true);
            journeyRepository.save(journey);
            return new MobilePaymentInitiateResponse(
                    operation.getId(),
                    operation.getStatus().name(),
                    operation.getPspReference(),
                    null
            );
        }

        JawupayInitiateResponse pspResponse = paymentService.initiatePayment(
                operation.getId(),
                idempotencyKey,
                plan.getPrice(),
                plan.getCurrency(),
                accountHolderId,
                actorUserId,
                operation.getDescription()
        );
        Operation pendingOperation = operationService.markPendingPsp(operation.getId(), pspResponse.pspReference());
        updatePaymentPending(journey, pendingOperation.getId(), pspResponse.pspReference(), false);
        journeyRepository.save(journey);

        return new MobilePaymentInitiateResponse(
                pendingOperation.getId(),
                pendingOperation.getStatus().name(),
                pspResponse.pspReference(),
                pspResponse.checkoutUrl()
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
        MobileOnboardingJourney journey = resolveDerivedJourneySnapshot(accountHolderId);
        boolean active = membershipService.findLatestMembership(accountHolderId)
                .map(this::isMembershipActive)
                .orElse(false);
        boolean networkReady = journey.isNetworkLinked() && mlmGraphSyncService.isNetworkReady(accountHolderId);
        return new MobileOnboardingStatusResponse(
                journey.getStatus().name(),
                completedSteps(journey),
                nextStep(journey, active),
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

    private MobileOnboardingJourney resolveDerivedJourneySnapshot(UUID accountHolderId) {
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

        Membership latestMembership = membershipService.findLatestMembership(accountHolderId).orElse(null);
        if (latestMembership != null && isMembershipActive(latestMembership)) {
            promoteStatus(copy, MobileOnboardingJourneyStatus.ACTIVE_MEMBER);
        }

        if (mlmRelationshipRepository.existsByDescendantId(accountHolderId) || copy.isNetworkLinked()) {
            copy.setNetworkLinked(true);
            promoteStatus(copy, MobileOnboardingJourneyStatus.NETWORK_LINKED);
        }

        if (copy.isOnboardingCompleted()) {
            promoteStatus(copy, MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED);
        } else if (copy.getStatus() == MobileOnboardingJourneyStatus.NETWORK_LINKED) {
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

        ensureNetworkLinked(journey, actorUserId);
        if (journey.isNetworkLinked() && promoteStatus(journey, MobileOnboardingJourneyStatus.NETWORK_LINKED)) {
            publish(journey.getAccountHolderId(), MobileOnboardingEventType.NETWORK_LINKED, Map.of());
        }

        if (journey.isNetworkLinked()) {
            journey.setOnboardingCompleted(true);
            if (promoteStatus(journey, MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED)) {
                publish(journey.getAccountHolderId(), MobileOnboardingEventType.ONBOARDING_COMPLETED, Map.of());
            }
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

    private boolean isFreePlan(SubscriptionPlan plan) {
        return plan.getGrade() == SubscriptionGrade.FREE
                || (plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) == 0);
    }

    private boolean isMembershipActive(Membership membership) {
        return membership.getStatus() == MembershipStatus.ACTIVE
                && (membership.getExpiresAt() == null || membership.getExpiresAt().isAfter(OffsetDateTime.now()));
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

    private List<String> completedSteps(MobileOnboardingJourney journey) {
        List<String> steps = new ArrayList<>();
        for (MobileOnboardingJourneyStatus status : EnumSet.allOf(MobileOnboardingJourneyStatus.class)) {
            if (status.ordinal() <= journey.getStatus().ordinal()) {
                steps.add(status.name());
            }
        }
        return List.copyOf(steps);
    }

    private MobileOnboardingStatusResponse.NextStep nextStep(MobileOnboardingJourney journey, boolean activeMembership) {
        if (activeMembership || journey.getStatus() == MobileOnboardingJourneyStatus.ONBOARDING_COMPLETED) {
            return null;
        }
        return switch (journey.getStatus()) {
            case REGISTERED, AUTHENTICATED -> new MobileOnboardingStatusResponse.NextStep(
                    "SELECT_COUNTRY",
                    "POST",
                    "/api/mobile/onboarding/select-country",
                    "Select your country to continue"
            );
            case COUNTRY_SELECTED -> new MobileOnboardingStatusResponse.NextStep(
                    "SET_SPONSOR",
                    "POST",
                    "/api/mobile/onboarding/set-sponsor",
                    "Set sponsor or continue without sponsor"
            );
            case SPONSOR_SET -> new MobileOnboardingStatusResponse.NextStep(
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
            case PAYMENT_CONFIRMED, ACTIVE_MEMBER -> new MobileOnboardingStatusResponse.NextStep(
                    "NETWORK_LINKING",
                    "GET",
                    "/api/mobile/onboarding/status",
                    "Network linking is in progress"
            );
            case NETWORK_LINKED -> new MobileOnboardingStatusResponse.NextStep(
                    "COMPLETE_ONBOARDING",
                    "GET",
                    "/api/mobile/onboarding/status",
                    "Finalize onboarding"
            );
            case ONBOARDING_COMPLETED -> null;
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
