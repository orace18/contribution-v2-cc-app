package com.ccapp.mobile.payments.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateRequest;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateResponse;
import com.ccapp.mobile.payments.dto.MobilePaymentMethodResponse;
import com.ccapp.mobile.payments.dto.MobilePaymentMethodsResponse;
import com.ccapp.mobile.payments.dto.MobilePaymentStatusResponse;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.payments.subscription.service.SubscriptionPaymentFinalizationService;
import com.ccapp.payments.subscription.service.SubscriptionService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.psp.client.JawuPayPspException;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayPaymentMethod;
import com.ccapp.wallet.service.WalletDebitFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobilePaymentService {

    private static final String WALLET_PAYMENT_METHOD_ID = "CC_WALLET";
    private static final String MEMBERSHIP_PAYMENT_TYPE = "MEMBERSHIP";

    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final OperationRepository operationRepository;
    private final JawuPayPspClient jawuPayPspClient;
    private final SubscriptionService subscriptionService;
    private final WalletDebitFacade walletDebitFacade;
    private final MembershipService membershipService;
    private final SubscriptionPaymentFinalizationService subscriptionPaymentFinalizationService;

    @Transactional(noRollbackFor = JawuPayPspException.class)
    public MobilePaymentInitiateResponse initiate(UUID actorUserId,
                                                  UUID accountHolderId,
                                                  MobilePaymentInitiateRequest request) {
        if (request == null) {
            throw new BusinessException("Payment request is required");
        }
        if (isWalletMembershipPayment(request)) {
            return initiateWalletMembershipPayment(actorUserId, accountHolderId, request);
        }
        return mobileOnboardingJourneyService.initiatePaidPlanPayment(
                actorUserId,
                accountHolderId,
                request.idempotencyKey(),
                request.country(),
                request.paymentMethodId(),
                request.msisdn(),
                request.operatorHint()
        );
    }

    @Transactional
    public MobilePaymentStatusResponse getStatus(UUID accountHolderId, UUID operationId) {
        Operation operation = operationRepository.findByIdAndAccountHolderId(operationId, accountHolderId)
                .orElseThrow(() -> new NotFoundException("Payment operation not found"));
        if (operation.getType() == OperationType.SUBSCRIPTION && operation.getStatus() == OperationStatus.CONFIRMED) {
            subscriptionPaymentFinalizationService.finalizeConfirmedOperation(operation.getId());
        }

        String mobileStatus = toMobileStatus(operation.getStatus());
        log.info("Mobile payment status response operationId={} accountHolderId={} operationStatus={} pspStatus={} mobileStatus={} paymentId={} providerReference={} pspReference={}",
                operation.getId(),
                accountHolderId,
                operation.getStatus(),
                operation.getPspStatus(),
                mobileStatus,
                operation.getPaymentId(),
                operation.getProviderReference(),
                operation.getPspReference());
        return new MobilePaymentStatusResponse(
                operation.getId(),
                mobileStatus,
                resolveStatusMessage(operation, mobileStatus),
                operation.getPspReference(),
                operation.getAmount(),
                operation.getCurrency(),
                operation.getPaymentMethod(),
                operation.getUpdatedAt(),
                resolveFailureReason(operation)
        );
    }

    @Transactional(readOnly = true)
    public MobilePaymentMethodsResponse getPaymentMethods(String country) {
        String normalizedCountry = normalizeCountry(country);
        List<MobilePaymentMethodResponse> methods = jawuPayPspClient.getPaymentMethods(normalizedCountry).stream()
                .map(this::toMobilePaymentMethod)
                .toList();
        return new MobilePaymentMethodsResponse(methods);
    }

    private MobilePaymentMethodResponse toMobilePaymentMethod(JawuPayPaymentMethod method) {
        String id = method == null || method.id() == null ? "" : method.id().trim();
        String label = method == null || method.label() == null ? "" : method.label().trim();
        String operator = resolveOperator(id, label);
        String name = label.isBlank() ? id : label;
        return new MobilePaymentMethodResponse(id, name, operator);
    }

    private MobilePaymentInitiateResponse initiateWalletMembershipPayment(UUID actorUserId,
                                                                          UUID accountHolderId,
                                                                          MobilePaymentInitiateRequest request) {
        if (request.planId() == null) {
            throw new BusinessException("planId is required");
        }
        String pin = request.pin() == null ? "" : request.pin().trim();
        if (pin.isBlank()) {
            throw new BusinessException("INVALID_PIN");
        }

        SubscriptionService.SubscriptionPurchaseContext context = subscriptionService.preparePurchaseContext(
                accountHolderId,
                actorUserId,
                request.planId(),
                request.idempotencyKey()
        );

        WalletDebitFacade.WalletDebitExecutionResult result;
        try {
            result = walletDebitFacade.executeSecureDebit(
                    new WalletDebitFacade.SecureDebitRequest(
                            context.accountHolderId(),
                            context.accountHolderId(),
                            context.actorUserId(),
                            null,
                            OperationType.SUBSCRIPTION,
                            context.plan().getPrice(),
                            context.plan().getCurrency(),
                            context.idempotencyKey(),
                            context.description(),
                            context.fingerprint(),
                            context.destinationAccountCode(),
                            null,
                            null,
                            null,
                            pin,
                            null,
                            null,
                            false,
                            false,
                            "Subscription wallet debit",
                            "Subscription revenue recognition",
                            null,
                            null,
                            WalletDebitFacade.SecureDebitPostingMode.CONFIRM_ONLY,
                            context.countryCode()
                    )
            );
        } catch (BusinessException ex) {
            throw remapWalletPaymentError(ex);
        }

        if (result.otpRequired()) {
            throw new BusinessException("WALLET_OTP_REQUIRED");
        }

        membershipService.createPendingMembership(result.operationId(), accountHolderId, context.plan());
        subscriptionPaymentFinalizationService.finalizeConfirmedOperation(result.operationId());

        return new MobilePaymentInitiateResponse(
                result.operationId(),
                result.status(),
                null,
                null
        );
    }

    private String toMobileStatus(OperationStatus status) {
        if (status == null) {
            return "PENDING";
        }
        return switch (status) {
            case INITIATED -> "INITIATED";
            case PENDING_PSP -> "PENDING";
            case CONFIRMED -> "SUCCESS";
            case FAILED, EXPIRED, CANCELED -> "FAILED";
            default -> "PENDING";
        };
    }

    private String resolveStatusMessage(Operation operation, String mobileStatus) {
        if ("SUCCESS".equals(mobileStatus)) {
            return "Payment confirmed";
        }
        if ("FAILED".equals(mobileStatus)) {
            return firstNonBlank(resolveFailureReason(operation), "Payment failed");
        }
        if ("INITIATED".equals(mobileStatus)) {
            return "Payment initialized";
        }
        return "Payment pending confirmation";
    }

    private String resolveFailureReason(Operation operation) {
        if (operation == null || operation.getStatus() == null) {
            return null;
        }
        return switch (operation.getStatus()) {
            case FAILED, EXPIRED, CANCELED -> firstNonBlank(operation.getPspStatus(), operation.getDescription(), operation.getStatus().name());
            default -> null;
        };
    }

    private String resolveOperator(String id, String label) {
        String haystack = (firstNonBlank(id, "") + " " + firstNonBlank(label, "")).toUpperCase(Locale.ROOT);
        if (haystack.contains("MTN")) {
            return "MTN";
        }
        if (haystack.contains("MOOV")) {
            return "MOOV";
        }
        if (haystack.contains("CELTIIS") || haystack.contains("CELTIS")) {
            return "CELTIS";
        }
        return "";
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            throw new BusinessException("country is required");
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 3) {
            throw new BusinessException("country must be ISO code length 2 or 3");
        }
        return normalized;
    }

    private boolean isWalletMembershipPayment(MobilePaymentInitiateRequest request) {
        return request.paymentMethodId() != null
                && WALLET_PAYMENT_METHOD_ID.equalsIgnoreCase(request.paymentMethodId().trim())
                && (request.paymentType() == null
                || request.paymentType().isBlank()
                || MEMBERSHIP_PAYMENT_TYPE.equalsIgnoreCase(request.paymentType().trim()));
    }

    private BusinessException remapWalletPaymentError(BusinessException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().trim();
        if (message.startsWith("Insufficient available balance")) {
            return new BusinessException("INSUFFICIENT_BALANCE");
        }
        if (message.startsWith("No active wallet found for account holder")
                || message.startsWith("Wallet is not active:")) {
            return new BusinessException("WALLET_NOT_ACTIVE");
        }
        if (message.startsWith("WALLET_PIN_")) {
            return new BusinessException("INVALID_PIN");
        }
        return ex;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
