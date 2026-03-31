package com.ccapp.payments.operation.service;

import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class OperationService {

    private final OperationRepository operationRepository;

    @Transactional
    public Operation createInitiatedOperation(UUID accountHolderId,
                                              UUID actorUserId,
                                              OperationType type,
                                              BigDecimal amount,
                                              String currency,
                                              String idempotencyKey,
                                              String description) {
        return createInitiatedOperation(accountHolderId, actorUserId, type, amount, currency, idempotencyKey, description, null, OperationRouting.none());
    }

    @Transactional
    public Operation createInitiatedOperation(UUID accountHolderId,
                                              UUID actorUserId,
                                              OperationType type,
                                              BigDecimal amount,
                                              String currency,
                                              String idempotencyKey,
                                              String description,
                                              UUID planId) {
        return createInitiatedOperation(
                accountHolderId,
                actorUserId,
                type,
                amount,
                currency,
                idempotencyKey,
                description,
                planId,
                OperationRouting.none()
        );
    }

    @Transactional
    public Operation createInitiatedOperation(UUID accountHolderId,
                                              UUID actorUserId,
                                              OperationType type,
                                              BigDecimal amount,
                                              String currency,
                                              String idempotencyKey,
                                              String description,
                                              UUID planId,
                                              OperationRouting routing) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String normalizedCurrency = normalizeCurrency(currency);
        OperationRouting normalizedRouting = normalizeRouting(routing);
        validateAmount(amount);

        return operationRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                .map(existing -> ensureCompatibleExistingOperation(
                        existing,
                        accountHolderId,
                        actorUserId,
                        type,
                        amount,
                        normalizedCurrency,
                        planId,
                        normalizedRouting
                ))
                .orElseGet(() -> operationRepository.save(Operation.builder()
                        .accountHolderId(accountHolderId)
                        .actorUserId(actorUserId)
                        .type(type)
                        .status(OperationStatus.INITIATED)
                        .amount(amount)
                        .currency(normalizedCurrency)
                        .idempotencyKey(normalizedIdempotencyKey)
                        .planId(planId)
                        .destinationAccountHolderId(normalizedRouting.destinationAccountHolderId())
                        .destinationWalletId(normalizedRouting.destinationWalletId())
                        .destinationPhone(normalizedRouting.destinationPhone())
                        .destinationAccountCode(normalizedRouting.destinationAccountCode())
                        .description(normalizeDescription(description))
                        .build()));
    }

    @Transactional
    public Operation createInitiatedOperation(UUID accountHolderId,
                                              OperationType type,
                                              BigDecimal amount,
                                              String currency,
                                              String idempotencyKey,
                                              String description) {
        return createInitiatedOperation(accountHolderId, accountHolderId, type, amount, currency, idempotencyKey, description, null, OperationRouting.none());
    }

    /**
     * Creates an operation in PAYOUT_INITIATED state. Used exclusively for outbound payout flows
     * (e.g. JawuPay payout) where the lifecycle starts with a distinct payout-initiation status
     * rather than the generic INITIATED state.
     */
    @Transactional
    public Operation createPayoutInitiatedOperation(UUID accountHolderId,
                                                    UUID actorUserId,
                                                    OperationType type,
                                                    BigDecimal amount,
                                                    String currency,
                                                    String idempotencyKey,
                                                    String description,
                                                    OperationRouting routing) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String normalizedCurrency = normalizeCurrency(currency);
        OperationRouting normalizedRouting = normalizeRouting(routing);
        validateAmount(amount);

        return operationRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                .map(existing -> ensureCompatibleExistingOperation(
                        existing,
                        accountHolderId,
                        actorUserId,
                        type,
                        amount,
                        normalizedCurrency,
                        null,
                        normalizedRouting
                ))
                .orElseGet(() -> operationRepository.save(Operation.builder()
                        .accountHolderId(accountHolderId)
                        .actorUserId(actorUserId)
                        .type(type)
                        .status(OperationStatus.PAYOUT_INITIATED)
                        .amount(amount)
                        .currency(normalizedCurrency)
                        .idempotencyKey(normalizedIdempotencyKey)
                        .destinationAccountHolderId(normalizedRouting.destinationAccountHolderId())
                        .destinationWalletId(normalizedRouting.destinationWalletId())
                        .destinationPhone(normalizedRouting.destinationPhone())
                        .destinationAccountCode(normalizedRouting.destinationAccountCode())
                        .description(normalizeDescription(description))
                        .build()));
    }

    @Transactional(readOnly = true)
    public Optional<Operation> findByIdempotencyKey(String idempotencyKey) {
        return operationRepository.findByIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
    }

    @Transactional(readOnly = true)
    public Optional<Operation> findByPaymentIdOrPspReference(String paymentId) {
        String normalizedPaymentId = normalizeNullableText(paymentId, 128);
        if (normalizedPaymentId == null) {
            return Optional.empty();
        }
        return operationRepository.findByPaymentId(normalizedPaymentId)
                .or(() -> operationRepository.findByPspReference(normalizedPaymentId));
    }

    @Transactional(readOnly = true)
    public Optional<Operation> findByPayoutIdOrPspReference(String payoutId) {
        String normalizedPayoutId = normalizeNullableText(payoutId, 128);
        if (normalizedPayoutId == null) {
            return Optional.empty();
        }
        return operationRepository.findByPayoutId(normalizedPayoutId)
                .or(() -> operationRepository.findByPspReference(normalizedPayoutId));
    }

    @Transactional(readOnly = true)
    public Optional<Operation> findByProviderReferenceOrPspReference(String providerReference) {
        String normalizedProviderReference = normalizeNullableText(providerReference, 128);
        if (normalizedProviderReference == null) {
            return Optional.empty();
        }
        return operationRepository.findByProviderReference(normalizedProviderReference)
                .or(() -> operationRepository.findByPspReference(normalizedProviderReference));
    }

    @Transactional(readOnly = true)
    public Operation assertCompatibleExistingOperation(Operation existing,
                                                       UUID accountHolderId,
                                                       UUID actorUserId,
                                                       OperationType type,
                                                       BigDecimal amount,
                                                       String currency,
                                                       UUID planId,
                                                       OperationRouting routing) {
        return ensureCompatibleExistingOperation(
                existing,
                accountHolderId,
                actorUserId,
                type,
                amount,
                normalizeCurrency(currency),
                planId,
                normalizeRouting(routing)
        );
    }

    @Transactional
    public Operation assignPspReference(UUID operationId, String pspReference) {
        return recordPspMetadata(operationId, PspMetadata.legacyReference(pspReference));
    }

    @Transactional
    public Operation recordPspMetadata(UUID operationId, PspMetadata metadata) {
        Operation operation = getById(operationId);
        mergePspMetadata(operation, metadata);
        return operationRepository.save(operation);
    }

    @Transactional
    public Operation markPendingPsp(UUID operationId, String pspReference) {
        return markPendingPsp(operationId, PspMetadata.legacyReference(pspReference));
    }

    @Transactional
    public Operation markPendingPsp(UUID operationId, PspMetadata metadata) {
        Operation operation = getById(operationId);
        mergePspMetadata(operation, metadata);
        if (operation.getStatus() == OperationStatus.PENDING_PSP) {
            return operationRepository.save(operation);
        }
        if (operation.getStatus() != OperationStatus.INITIATED
                && operation.getStatus() != OperationStatus.PAYOUT_INITIATED) {
            throw new BusinessException("Operation cannot move to PENDING_PSP");
        }
        operation.setStatus(OperationStatus.PENDING_PSP);
        return operationRepository.save(operation);
    }

    @Transactional
    public Operation confirmByPspReference(String pspReference, BigDecimal amount, String currency) {
        Operation operation = operationRepository.findByPspReference(pspReference)
                .orElseThrow(() -> new NotFoundException("Operation not found for PSP reference"));

        if (operation.getAmount().compareTo(amount) != 0) {
            throw new BusinessException("Operation amount mismatch");
        }
        if (!operation.getCurrency().equals(currency)) {
            throw new BusinessException("Operation currency mismatch");
        }

        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            return operation;
        }
        if (operation.getStatus() != OperationStatus.INITIATED
                && operation.getStatus() != OperationStatus.PENDING_PSP) {
            throw new BusinessException("Operation not confirmable");
        }

        operation.setStatus(OperationStatus.CONFIRMED);
        return operationRepository.save(operation);
    }

    @Transactional
    public Operation confirmOperation(UUID operationId) {
        Operation operation = getById(operationId);
        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            return operation;
        }
        if (operation.getStatus() != OperationStatus.INITIATED
                && operation.getStatus() != OperationStatus.PENDING_PSP) {
            throw new BusinessException("Operation not confirmable");
        }
        operation.setStatus(OperationStatus.CONFIRMED);
        return operationRepository.save(operation);
    }

    @Transactional
    public Operation failByPspReference(String pspReference) {
        Operation operation = getByPspReference(pspReference);
        return markFailed(operation.getId());
    }

    @Transactional
    public Operation expireByPspReference(String pspReference) {
        Operation operation = getByPspReference(pspReference);
        return markExpired(operation.getId());
    }

    @Transactional
    public Operation markFailed(UUID operationId) {
        Operation operation = getById(operationId);
        if (operation.getStatus() == OperationStatus.FAILED) {
            return operation;
        }
        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            throw new BusinessException("Confirmed operation cannot be failed");
        }
        operation.setStatus(OperationStatus.FAILED);
        return operationRepository.save(operation);
    }

    @Transactional
    public Operation markExpired(UUID operationId) {
        Operation operation = getById(operationId);
        if (operation.getStatus() == OperationStatus.EXPIRED) {
            return operation;
        }
        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            throw new BusinessException("Confirmed operation cannot be expired");
        }
        operation.setStatus(OperationStatus.EXPIRED);
        return operationRepository.save(operation);
    }

    @Transactional(readOnly = true)
    public Operation getById(UUID operationId) {
        return operationRepository.findById(operationId)
                .orElseThrow(() -> new NotFoundException("Operation not found"));
    }

    @Transactional(readOnly = true)
    public Operation getByPspReference(String pspReference) {
        return operationRepository.findByPspReference(pspReference)
                .orElseThrow(() -> new NotFoundException("Operation not found for PSP reference"));
    }

    private Operation ensureCompatibleExistingOperation(Operation existing,
                                                        UUID accountHolderId,
                                                        UUID actorUserId,
                                                        OperationType type,
                                                        BigDecimal amount,
                                                        String currency,
                                                        UUID planId,
                                                        OperationRouting routing) {
        boolean compatible = Objects.equals(existing.getAccountHolderId(), accountHolderId)
                && Objects.equals(existing.getActorUserId(), actorUserId)
                && existing.getType() == type
                && existing.getAmount().compareTo(amount) == 0
                && existing.getCurrency().equalsIgnoreCase(currency)
                && Objects.equals(existing.getPlanId(), planId)
                && Objects.equals(existing.getDestinationAccountHolderId(), routing.destinationAccountHolderId())
                && Objects.equals(existing.getDestinationWalletId(), routing.destinationWalletId())
                && Objects.equals(normalizeNullableText(existing.getDestinationPhone(), 64), routing.destinationPhone())
                && Objects.equals(normalizeNullableText(existing.getDestinationAccountCode(), 64), routing.destinationAccountCode());
        if (!compatible) {
            throw new ConflictException("Idempotency key already used by another operation signature");
        }
        return existing;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Amount must be positive");
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("Idempotency key is required");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 128) {
            throw new BusinessException("Idempotency key must be <= 128 characters");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalized = description.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private void mergePspMetadata(Operation operation, PspMetadata metadata) {
        if (operation == null || metadata == null) {
            return;
        }

        String explicitPspReference = normalizeNullableText(metadata.pspReference(), 128);
        String paymentId = normalizeNullableText(metadata.paymentId(), 128);
        String payoutId = normalizeNullableText(metadata.payoutId(), 128);
        String providerReference = normalizeNullableText(metadata.providerReference(), 128);
        String derivedPspReference = normalizePspReference(explicitPspReference, paymentId, payoutId, providerReference);
        boolean hasStructuredIdentifiers = paymentId != null || payoutId != null || providerReference != null;

        if (!hasStructuredIdentifiers) {
            assignIfCompatible("PSP reference", operation.getPspReference(), derivedPspReference, operation::setPspReference);
        } else if (operation.getPspReference() == null && derivedPspReference != null) {
            operation.setPspReference(derivedPspReference);
        }
        assignIfCompatible("payment id", operation.getPaymentId(), paymentId, operation::setPaymentId);
        assignIfCompatible("payout id", operation.getPayoutId(), payoutId, operation::setPayoutId);
        assignIfCompatible("provider reference", operation.getProviderReference(), providerReference, operation::setProviderReference);
        assignIfCompatible("redirect URL", operation.getRedirectUrl(), normalizeNullableText(metadata.redirectUrl(), 1024), operation::setRedirectUrl);
        assignIfCompatible("payment method", operation.getPaymentMethod(), normalizeNullableText(metadata.paymentMethod(), 64), operation::setPaymentMethod);
        assignIfCompatible("PSP country", operation.getPspCountry(), normalizePspCountry(metadata.pspCountry()), operation::setPspCountry);
        replaceIfPresent(operation::setPspStatus, normalizePspStatus(metadata.pspStatus()));
        replaceIfPresent(operation::setPspPayload, normalizePspPayload(metadata.pspPayload()));
    }

    private OperationRouting normalizeRouting(OperationRouting routing) {
        if (routing == null) {
            return OperationRouting.none();
        }
        return new OperationRouting(
                routing.destinationAccountHolderId(),
                routing.destinationWalletId(),
                normalizeNullableText(routing.destinationPhone(), 64),
                normalizeNullableText(routing.destinationAccountCode(), 64)
        );
    }

    private String normalizeNullableText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizePspReference(String pspReference,
                                         String paymentId,
                                         String payoutId,
                                         String providerReference) {
        String normalized = normalizeNullableText(pspReference, 128);
        if (normalized != null) {
            return normalized;
        }
        if (paymentId != null) {
            return paymentId;
        }
        if (payoutId != null) {
            return payoutId;
        }
        return providerReference;
    }

    private String normalizePspStatus(String pspStatus) {
        String normalized = normalizeNullableText(pspStatus, 64);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizePspCountry(String pspCountry) {
        String normalized = normalizeNullableText(pspCountry, 5);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizePspPayload(String pspPayload) {
        if (pspPayload == null || pspPayload.isBlank()) {
            return null;
        }
        String normalized = pspPayload.trim();
        return normalized.length() <= 16000 ? normalized : normalized.substring(0, 16000);
    }

    private void assignIfCompatible(String fieldLabel,
                                    String currentValue,
                                    String candidateValue,
                                    Consumer<String> setter) {
        if (candidateValue == null) {
            return;
        }
        if (currentValue != null && !currentValue.equals(candidateValue)) {
            throw new ConflictException("Operation already linked to another " + fieldLabel);
        }
        setter.accept(candidateValue);
    }

    private void replaceIfPresent(Consumer<String> setter, String candidateValue) {
        if (candidateValue != null) {
            setter.accept(candidateValue);
        }
    }

    public record PspMetadata(
            String pspReference,
            String paymentId,
            String payoutId,
            String providerReference,
            String redirectUrl,
            String paymentMethod,
            String pspStatus,
            String pspPayload,
            String pspCountry
    ) {
        public PspMetadata(String pspReference,
                           String paymentId,
                           String payoutId,
                           String providerReference,
                           String redirectUrl,
                           String paymentMethod,
                           String pspStatus,
                           String pspPayload) {
            this(pspReference, paymentId, payoutId, providerReference, redirectUrl, paymentMethod, pspStatus, pspPayload, null);
        }

        public static PspMetadata legacyReference(String pspReference) {
            return new PspMetadata(pspReference, null, null, null, null, null, null, null, null);
        }

        public static PspMetadata payment(String paymentId,
                                          String providerReference,
                                          String redirectUrl,
                                          String paymentMethod,
                                          String pspStatus) {
            return payment(paymentId, providerReference, redirectUrl, paymentMethod, pspStatus, null);
        }

        public static PspMetadata payment(String paymentId,
                                          String providerReference,
                                          String redirectUrl,
                                          String paymentMethod,
                                          String pspStatus,
                                          String pspCountry) {
            return new PspMetadata(paymentId, paymentId, null, providerReference, redirectUrl, paymentMethod, pspStatus, null, pspCountry);
        }

        public static PspMetadata payout(String payoutId,
                                         String providerReference,
                                         String paymentMethod,
                                         String pspStatus) {
            return payout(payoutId, providerReference, paymentMethod, pspStatus, null);
        }

        public static PspMetadata payout(String payoutId,
                                         String providerReference,
                                         String paymentMethod,
                                         String pspStatus,
                                         String pspCountry) {
            return new PspMetadata(payoutId, null, payoutId, providerReference, null, paymentMethod, pspStatus, null, pspCountry);
        }

        public static PspMetadata statusOnly(String pspStatus) {
            return new PspMetadata(null, null, null, null, null, null, pspStatus, null, null);
        }
    }

    public record OperationRouting(
            UUID destinationAccountHolderId,
            UUID destinationWalletId,
            String destinationPhone,
            String destinationAccountCode
    ) {
        public static OperationRouting none() {
            return new OperationRouting(null, null, null, null);
        }
    }
}
