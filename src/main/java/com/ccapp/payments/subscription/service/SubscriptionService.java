package com.ccapp.payments.subscription.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.payments.psp.client.JawupayClient;
import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.dto.InitiateSubscriptionRequest;
import com.ccapp.payments.subscription.dto.InitiateSubscriptionResponse;
import com.ccapp.payments.subscription.dto.SubscriptionPlanResponse;
import com.ccapp.payments.subscription.mapper.SubscriptionPlanMapper;
import com.ccapp.payments.subscription.repository.SubscriptionPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final CountryContextService countryContextService;
    private final AccessControlService accessControlService;
    private final OperationService operationService;
    private final JawupayClient jawupayClient;
    private final CountryConfigResolver countryConfigResolver;
    private final MembershipService membershipService;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getPlans(UUID accountHolderId) {
        CountryContext countryContext = countryContextService.resolve(accountHolderId);
        return subscriptionPlanRepository.findByCountryAndActiveTrueOrderByPriceAsc(countryContext.serviceCountryCode()).stream()
                .map(subscriptionPlanMapper::toResponse)
                .toList();
    }

    @Transactional
    public InitiateSubscriptionResponse initiate(UUID accountHolderId,
                                                 UUID actorUserId,
                                                 InitiateSubscriptionRequest request) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        CountryContext countryContext = countryContextService.resolve(accountHolderId);

        SubscriptionPlan plan = subscriptionPlanRepository
                .findByCodeAndCountryAndActiveTrue(request.planCode(), countryContext.serviceCountryCode())
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));
        if (!plan.getCountry().equalsIgnoreCase(countryContext.serviceCountryCode())) {
            throw new BusinessException("Subscription plan country must match account holder service country");
        }
        CountryConfigResolver.ResolvedConfig membershipConfig = countryConfigResolver.resolveActiveConfig(
                countryContext.serviceCountryCode(),
                CountryConfigType.MEMBERSHIP
        );
        CountryConfigResolver.ResolvedConfig paymentConfig = countryConfigResolver.resolveActiveConfig(
                countryContext.serviceCountryCode(),
                CountryConfigType.PAYMENT
        );
        assertPlanAllowedByConfiguration(plan, membershipConfig);
        assertPaymentConfigurationIsUsable(paymentConfig);

        String idempotencyKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? "sub:" + UUID.randomUUID()
                : request.idempotencyKey();

        Operation operation = operationService.createInitiatedOperation(
                accountHolderId,
                actorUserId,
                OperationType.SUBSCRIPTION,
                plan.getPrice(),
                plan.getCurrency(),
                idempotencyKey,
                "Subscription " + plan.getCode()
        );

        JawupayInitiateResponse pspResponse = jawupayClient.initiatePayment(new JawupayInitiateRequest(
                operation.getId(),
                idempotencyKey,
                operation.getAmount(),
                operation.getCurrency(),
                accountHolderId,
                actorUserId,
                operation.getDescription()
        ));

        operationService.assignPspReference(operation.getId(), pspResponse.pspReference());
        membershipService.createPendingMembership(operation.getId(), accountHolderId, plan);

        return new InitiateSubscriptionResponse(
                operation.getId(),
                pspResponse.pspReference(),
                pspResponse.checkoutUrl(),
                operation.getStatus().name()
        );
    }

    @Transactional
    public InitiateSubscriptionResponse initiate(UUID accountHolderId, InitiateSubscriptionRequest request) {
        return initiate(accountHolderId, accountHolderId, request);
    }

    private void assertPlanAllowedByConfiguration(SubscriptionPlan plan,
                                                  CountryConfigResolver.ResolvedConfig membershipConfig) {
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(membershipConfig);
        JsonNode plans = payload.get("plans");
        if (plans == null || !plans.isArray() || plans.isEmpty()) {
            return;
        }

        boolean matchingPlan = false;
        for (JsonNode configuredPlan : plans) {
            if (!configuredPlan.isObject()) {
                continue;
            }
            JsonNode codeNode = configuredPlan.get("code");
            JsonNode nameNode = configuredPlan.get("name");
            boolean codeMatches = codeNode != null && codeNode.isTextual()
                    && codeNode.asText().equalsIgnoreCase(plan.getCode());
            boolean nameMatches = nameNode != null && nameNode.isTextual()
                    && nameNode.asText().equalsIgnoreCase(plan.getName());
            if (codeMatches || nameMatches) {
                matchingPlan = true;
                break;
            }
        }
        if (!matchingPlan) {
            throw new BusinessException("Subscription plan is not allowed by membership configuration");
        }
    }

    private void assertPaymentConfigurationIsUsable(CountryConfigResolver.ResolvedConfig paymentConfig) {
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(paymentConfig);
        JsonNode pspMethods = payload.get("pspMethods");
        if (pspMethods != null && pspMethods.isArray() && pspMethods.isEmpty()) {
            throw new BusinessException("Payment configuration has no PSP methods enabled");
        }
    }
}
