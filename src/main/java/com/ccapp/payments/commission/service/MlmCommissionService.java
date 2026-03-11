package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.event.CommissionGeneratedEvent;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MlmCommissionService {

    private final MlmCommissionRepository mlmCommissionRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Value("${mlm.commission.rate-percent:5.0}")
    private BigDecimal commissionRatePercent;

    @Transactional
    public Optional<MlmCommission> calculateForOperation(Operation operation) {
        if (operation == null || operation.getType() != OperationType.SUBSCRIPTION) {
            return Optional.empty();
        }

        Optional<MlmCommission> existing = mlmCommissionRepository.findByOperationId(operation.getId());
        if (existing.isPresent()) {
            return existing;
        }

        BigDecimal commissionAmount = operation.getAmount()
                .multiply(commissionRatePercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        if (commissionAmount.signum() <= 0) {
            return Optional.empty();
        }

        MlmCommission saved = mlmCommissionRepository.save(MlmCommission.builder()
                .operationId(operation.getId())
                .beneficiaryAccountHolderId(operation.getAccountHolderId())
                .amount(commissionAmount)
                .currency(operation.getCurrency())
                .source(operation.getType().name())
                .build());

        domainEventPublisher.publish(CommissionGeneratedEvent.create(
                saved.getOperationId(),
                saved.getBeneficiaryAccountHolderId(),
                saved.getAmount(),
                saved.getCurrency(),
                saved.getSource()
        ));

        return Optional.of(saved);
    }
}
