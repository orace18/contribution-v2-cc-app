package com.ccapp.mobile.payments.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.mobile.onboarding.service.MobileOnboardingJourneyService;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateRequest;
import com.ccapp.mobile.payments.dto.MobilePaymentInitiateResponse;
import com.ccapp.mobile.payments.dto.MobilePaymentMethodResponse;
import com.ccapp.mobile.payments.dto.MobilePaymentMethodsResponse;
import com.ccapp.mobile.payments.dto.MobilePaymentStatusResponse;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.psp.client.JawuPayPspException;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayPaymentMethod;
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

    private final MobileOnboardingJourneyService mobileOnboardingJourneyService;
    private final OperationRepository operationRepository;
    private final JawuPayPspClient jawuPayPspClient;

    @Transactional(noRollbackFor = JawuPayPspException.class)
    public MobilePaymentInitiateResponse initiate(UUID actorUserId,
                                                  UUID accountHolderId,
                                                  MobilePaymentInitiateRequest request) {
        return mobileOnboardingJourneyService.initiatePaidPlanPayment(
                actorUserId,
                accountHolderId,
                request == null ? null : request.idempotencyKey(),
                request == null ? null : request.country(),
                request == null ? null : request.paymentMethodId(),
                request == null ? null : request.msisdn(),
                request == null ? null : request.operatorHint()
        );
    }

    @Transactional(readOnly = true)
    public MobilePaymentStatusResponse getStatus(UUID accountHolderId, UUID operationId) {
        Operation operation = operationRepository.findByIdAndAccountHolderId(operationId, accountHolderId)
                .orElseThrow(() -> new NotFoundException("Payment operation not found"));

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
