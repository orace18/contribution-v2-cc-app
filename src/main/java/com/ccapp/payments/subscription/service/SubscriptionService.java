package com.ccapp.payments.subscription.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.service.CountryService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
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
    private final AccountHolderService accountHolderService;
    private final CountryService countryService;
    private final AccessControlService accessControlService;
    private final OperationService operationService;
    private final JawupayClient jawupayClient;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getPlans(UUID accountHolderId) {
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        String countryCode = countryService.resolveByCodeOrThrow(accountHolder.getCountryCode()).getCode();
        return subscriptionPlanRepository.findByCountryAndActiveTrueOrderByPriceAsc(countryCode).stream()
                .map(subscriptionPlanMapper::toResponse)
                .toList();
    }

    @Transactional
    public InitiateSubscriptionResponse initiate(UUID accountHolderId,
                                                 UUID actorUserId,
                                                 InitiateSubscriptionRequest request) {
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        String countryCode = countryService.resolveByCodeOrThrow(accountHolder.getCountryCode()).getCode();

        SubscriptionPlan plan = subscriptionPlanRepository
                .findByCodeAndCountryAndActiveTrue(request.planCode(), countryCode)
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));

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
}
