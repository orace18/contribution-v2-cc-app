package com.ccapp.subscription.service;

import com.ccapp.accounting.operation.domain.entity.Operation;
import com.ccapp.accounting.operation.domain.enums.OperationType;
import com.ccapp.accounting.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.psp.client.JawupayClient;
import com.ccapp.psp.dto.JawupayInitiateRequest;
import com.ccapp.psp.dto.JawupayInitiateResponse;
import com.ccapp.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.subscription.dto.InitiateSubscriptionRequest;
import com.ccapp.subscription.dto.InitiateSubscriptionResponse;
import com.ccapp.subscription.dto.SubscriptionPlanResponse;
import com.ccapp.subscription.mapper.SubscriptionPlanMapper;
import com.ccapp.subscription.repository.SubscriptionPlanRepository;
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
    private final UserRepository userRepository;
    private final OperationService operationService;
    private final JawupayClient jawupayClient;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getPlans(String country) {
        return subscriptionPlanRepository.findByCountryAndActiveTrueOrderByPriceAsc(country).stream()
                .map(subscriptionPlanMapper::toResponse)
                .toList();
    }

    @Transactional
    public InitiateSubscriptionResponse initiate(UUID userId, InitiateSubscriptionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("User account is not active");
        }

        SubscriptionPlan plan = subscriptionPlanRepository
                .findByCodeAndCountryAndActiveTrue(request.planCode(), request.country())
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));

        String idempotencyKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? "sub:" + UUID.randomUUID()
                : request.idempotencyKey();

        Operation operation = operationService.createInitiatedOperation(
                userId,
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
                userId,
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
}
