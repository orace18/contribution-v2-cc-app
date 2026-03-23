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

    @Transactional(readOnly = true)
    public Optional<Operation> findByIdempotencyKey(String idempotencyKey) {
        return operationRepository.findByIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
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
        Operation operation = getById(operationId);
        if (operation.getPspReference() != null && !operation.getPspReference().equals(pspReference)) {
            throw new ConflictException("Operation already linked to another PSP reference");
        }
        operation.setPspReference(pspReference);
        return operationRepository.save(operation);
    }

    @Transactional
    public Operation markPendingPsp(UUID operationId, String pspReference) {
        Operation operation = assignPspReference(operationId, pspReference);
        if (operation.getStatus() == OperationStatus.PENDING_PSP) {
            return operation;
        }
        if (operation.getStatus() != OperationStatus.INITIATED) {
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
                && Objects.equals(normalizeNullableText(existing.getDestinationPhone()), routing.destinationPhone())
                && Objects.equals(normalizeNullableText(existing.getDestinationAccountCode()), routing.destinationAccountCode());
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

    private OperationRouting normalizeRouting(OperationRouting routing) {
        if (routing == null) {
            return OperationRouting.none();
        }
        return new OperationRouting(
                routing.destinationAccountHolderId(),
                routing.destinationWalletId(),
                normalizeNullableText(routing.destinationPhone()),
                normalizeNullableText(routing.destinationAccountCode())
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
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
