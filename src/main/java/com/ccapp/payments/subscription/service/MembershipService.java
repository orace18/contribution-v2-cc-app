package com.ccapp.payments.subscription.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.payments.service.PaymentService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final CountryConfigResolver countryConfigResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public Membership createPendingMembership(UUID operationId,
                                              UUID accountHolderId,
                                              SubscriptionPlan plan) {
        return membershipRepository.findByOperationId(operationId)
                .orElseGet(() -> membershipRepository.save(Membership.builder()
                        .operationId(operationId)
                        .accountHolderId(accountHolderId)
                        .planCode(plan.getCode())
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
        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            return membership;
        }
        if (operation.getStatus() != OperationStatus.CONFIRMED) {
            throw new BusinessException("Membership activation requires a confirmed operation");
        }

        CountryConfigResolver.ResolvedConfig membershipConfig = countryConfigResolver.resolveActiveConfig(
                membership.getCountryCode(),
                CountryConfigType.MEMBERSHIP
        );

        OffsetDateTime now = OffsetDateTime.now();
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setActivatedAt(now);
        membership.setExpiresAt(now.plusDays(30));
        membership.setConfigSnapshot(writeSnapshot(membershipConfig));
        membership.setConfigVersion(membershipConfig.version());

        return membershipRepository.save(membership);
    }

    private String writeSnapshot(CountryConfigResolver.ResolvedConfig membershipConfig) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("countryCode", membershipConfig.countryCode());
        root.put("membershipConfigVersion", membershipConfig.version());
        root.putNull("paymentConfigVersion");
        root.put("paymentProvider", PaymentService.PSP_PROVIDER);
        root.set("membership", countryConfigResolver.payloadAsObjectNode(membershipConfig));
        ObjectNode payment = objectMapper.createObjectNode();
        payment.put("provider", PaymentService.PSP_PROVIDER);
        payment.put("managedBy", PaymentService.PSP_PROVIDER);
        payment.put("methodAvailabilitySource", "JAWUPAY_DYNAMIC");
        root.set("payment", payment);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Unable to serialize membership configuration snapshot");
        }
    }
}
