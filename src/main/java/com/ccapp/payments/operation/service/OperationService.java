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
        return operationRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> operationRepository.save(Operation.builder()
                        .accountHolderId(accountHolderId)
                        .actorUserId(actorUserId)
                        .type(type)
                        .status(OperationStatus.INITIATED)
                        .amount(amount)
                        .currency(currency)
                        .idempotencyKey(idempotencyKey)
                        .description(description)
                        .build()));
    }

    @Transactional
    public Operation createInitiatedOperation(UUID accountHolderId,
                                              OperationType type,
                                              BigDecimal amount,
                                              String currency,
                                              String idempotencyKey,
                                              String description) {
        return createInitiatedOperation(
                accountHolderId,
                accountHolderId,
                type,
                amount,
                currency,
                idempotencyKey,
                description
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
        if (operation.getStatus() != OperationStatus.INITIATED) {
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
        if (operation.getStatus() != OperationStatus.INITIATED) {
            throw new BusinessException("Operation not confirmable");
        }
        operation.setStatus(OperationStatus.CONFIRMED);
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
}
