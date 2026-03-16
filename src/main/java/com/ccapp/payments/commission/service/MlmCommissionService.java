package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.event.CommissionGeneratedEvent;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.psp.service.SimpleRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlmCommissionService {

    private final MlmCommissionRepository mlmCommissionRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final FinancialAuditService financialAuditService;
    private final SimpleRateLimiter simpleRateLimiter;

    @Value("${mlm.commission.rate-percent:5.0}")
    private BigDecimal commissionRatePercent;

    @Value("${mlm.monitoring.commission-spike.threshold:10000}")
    private BigDecimal commissionSpikeThreshold;

    @Value("${mlm.commission.generation.max-events-per-minute:120}")
    private int maxCommissionEventsPerMinute;

    @Transactional
    public Optional<MlmCommission> calculateForOperation(Operation operation) {
        if (operation == null || operation.getType() != OperationType.SUBSCRIPTION) {
            return Optional.empty();
        }

        Optional<MlmCommission> existing = mlmCommissionRepository.findByOperationId(operation.getId());
        if (existing.isPresent()) {
            return existing;
        }

        String rateKey = "mlm-commission:" + operation.getAccountHolderId();
        if (!simpleRateLimiter.allow(rateKey, maxCommissionEventsPerMinute)) {
            throw new BusinessException("Commission generation rate limit exceeded");
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
                .commissionType(CommissionType.SUBSCRIPTION)
                .status(CommissionStatus.PENDING)
                .build());
        PlatformMonitoringHooks.onCommissionCalculated(saved.getCommissionType().name(), saved.getAmount());
        log.info("commission_calculated operationId={} beneficiaryId={} amount={} currency={} type={} createdAt={}",
                saved.getOperationId(),
                saved.getBeneficiaryAccountHolderId(),
                saved.getAmount(),
                saved.getCurrency(),
                saved.getCommissionType(),
                OffsetDateTime.now());

        financialAuditService.record(
                "COMMISSION_CREATED",
                saved.getId() != null ? saved.getId().toString() : saved.getOperationId().toString(),
                saved.getBeneficiaryAccountHolderId(),
                saved.getAmount(),
                Map.of(
                        "commissionType", saved.getCommissionType().name(),
                        "source", saved.getSource(),
                        "operationId", String.valueOf(saved.getOperationId())
                )
        );
        if (saved.getAmount().compareTo(commissionSpikeThreshold) >= 0) {
            log.warn("commission_spike_detected operationId={} beneficiaryId={} amount={} threshold={}",
                    saved.getOperationId(),
                    saved.getBeneficiaryAccountHolderId(),
                    saved.getAmount(),
                    commissionSpikeThreshold);
            financialAuditService.record(
                    "COMMISSION_SPIKE_DETECTED",
                    saved.getId().toString(),
                    saved.getBeneficiaryAccountHolderId(),
                    saved.getAmount(),
                    Map.of(
                            "operationId", String.valueOf(saved.getOperationId()),
                            "threshold", commissionSpikeThreshold.toPlainString()
                    )
            );
        }

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
