package com.ccapp.payments.subscription.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private static final String JAWUPAY_PROVIDER = "JAWUPAY";

    private final MembershipRepository membershipRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CountryConfigResolver countryConfigResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public Membership createPendingMembership(UUID operationId,
                                              UUID accountHolderId,
                                              SubscriptionPlan plan) {
        return membershipRepository.findByOperationId(operationId)
                .orElseGet(() -> membershipRepository.save(Membership.builder()
                        .operationId(operationId)
                        .planId(plan.getId())
                        .accountHolderId(accountHolderId)
                        .planCode(plan.getCode())
                        .planGrade(plan.getGrade())
                        .durationMonths(plan.getDurationMonths())
                        .countryCode(plan.getCountry())
                        .currency(plan.getCurrency())
                        .amount(plan.getPrice())
                        .status(MembershipStatus.PENDING)
                        .build()));
    }

    @Transactional
    public Membership activateForConfirmedOperation(Operation operation) {
        Membership membership = membershipRepository.findByOperationId(operation.getId())
                .orElseThrow(() -> new BusinessException("Membership not found for operation"));
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Membership activation requires a confirmed operation");
        }
        SubscriptionPlan plan = resolvePlanForOperation(operation, membership);
        CountryConfigResolver.ResolvedConfig membershipConfig = countryConfigResolver.resolveActiveConfig(
                membership.getCountryCode(),
                CountryConfigType.MEMBERSHIP
        );
        OffsetDateTime now = OffsetDateTime.now();
        if (isAlreadyActivatedForConfirmedOperation(membership, now)) {
            boolean changed = false;
            if (membership.getPlanId() == null) {
                membership.setPlanId(plan.getId());
                changed = true;
            }
            if (membership.getPlanCode() == null) {
                membership.setPlanCode(plan.getCode());
                changed = true;
            }
            if (membership.getPlanGrade() == null) {
                membership.setPlanGrade(plan.getGrade());
                changed = true;
            }
            if (membership.getDurationMonths() == null) {
                membership.setDurationMonths(plan.getDurationMonths());
                changed = true;
            }
            if (membership.getConfigSnapshot() == null || membership.getConfigVersion() == null) {
                membership.setConfigSnapshot(writeSnapshot(membershipConfig, operation));
                membership.setConfigVersion(membershipConfig.version());
                changed = true;
            }
            return changed ? membershipRepository.save(membership) : membership;
        }

        int durationDays = durationDays(plan);
        Membership existingActiveMembership = membershipRepository
                .findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        membership.getAccountHolderId(),
                        MembershipStatus.ACTIVE,
                        now
                )
                .filter(activeMembership -> !activeMembership.getId().equals(membership.getId()))
                .orElse(null);

        OffsetDateTime activatedAt = existingActiveMembership != null && existingActiveMembership.getActivatedAt() != null
                ? existingActiveMembership.getActivatedAt()
                : now;
        OffsetDateTime baseExpiry = existingActiveMembership != null
                && existingActiveMembership.getExpiresAt() != null
                && existingActiveMembership.getExpiresAt().isAfter(now)
                ? existingActiveMembership.getExpiresAt()
                : now;

        if (existingActiveMembership != null) {
            existingActiveMembership.setStatus(MembershipStatus.CANCELED);
            membershipRepository.save(existingActiveMembership);
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setPlanId(plan.getId());
        membership.setPlanCode(plan.getCode());
        membership.setPlanGrade(plan.getGrade());
        membership.setDurationMonths(plan.getDurationMonths());
        membership.setActivatedAt(activatedAt);
        membership.setExpiresAt(baseExpiry.plusDays(durationDays));
        membership.setConfigSnapshot(writeSnapshot(membershipConfig, operation));
        membership.setConfigVersion(membershipConfig.version());

        return membershipRepository.save(membership);
    }

    private boolean isAlreadyActivatedForConfirmedOperation(Membership membership, OffsetDateTime now) {
        return membership.getStatus() == MembershipStatus.ACTIVE
                && membership.getActivatedAt() != null
                && membership.getExpiresAt() != null
                && membership.getExpiresAt().isAfter(now);
    }

    @Transactional
    public void cancelPendingMembership(UUID operationId) {
        membershipRepository.findByOperationId(operationId)
                .filter(membership -> membership.getStatus() == MembershipStatus.PENDING)
                .ifPresent(membership -> {
                    membership.setStatus(MembershipStatus.CANCELED);
                    membershipRepository.save(membership);
                });
    }

    @Transactional(readOnly = true)
    public Optional<Membership> findLatestMembership(UUID accountHolderId) {
        return membershipRepository.findFirstByAccountHolderIdAndExpiresAtIsNotNullOrderByExpiresAtDescCreatedAtDesc(accountHolderId)
                .or(() -> membershipRepository.findFirstByAccountHolderIdOrderByExpiresAtDescCreatedAtDesc(accountHolderId));
    }

    private SubscriptionPlan resolvePlanForOperation(Operation operation, Membership membership) {
        UUID planId = operation.getPlanId() != null ? operation.getPlanId() : membership.getPlanId();
        if (planId == null) {
            throw new BusinessException("Subscription plan is required on the operation");
        }
        return subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Subscription plan not found for membership activation"));
    }

    private int durationDays(SubscriptionPlan plan) {
        if (plan.getDurationMonths() == null || plan.getDurationMonths() <= 0) {
            throw new BusinessException("Subscription plan durationMonths must be positive");
        }
        return Math.toIntExact(Duration.ofDays(plan.getDurationMonths() * 30L).toDays());
    }

    private String writeSnapshot(CountryConfigResolver.ResolvedConfig membershipConfig, Operation operation) {
        boolean walletBacked = operation.getPspReference() == null || operation.getPspReference().isBlank();
        String paymentProvider = walletBacked ? "WALLET" : JAWUPAY_PROVIDER;
        ObjectNode root = objectMapper.createObjectNode();
        root.put("countryCode", membershipConfig.countryCode());
        root.put("membershipConfigVersion", membershipConfig.version());
        root.putNull("paymentConfigVersion");
        root.put("paymentProvider", paymentProvider);
        root.set("membership", countryConfigResolver.payloadAsObjectNode(membershipConfig));
        ObjectNode payment = objectMapper.createObjectNode();
        payment.put("provider", paymentProvider);
        payment.put("managedBy", paymentProvider);
        payment.put("methodAvailabilitySource", walletBacked ? "WALLET_LEDGER" : "JAWUPAY_DYNAMIC");
        root.set("payment", payment);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Unable to serialize membership configuration snapshot");
        }
    }
}
