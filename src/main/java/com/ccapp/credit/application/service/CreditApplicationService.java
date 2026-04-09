package com.ccapp.credit.application.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.credit.application.domain.entity.CreditApplication;
import com.ccapp.credit.application.domain.enums.CreditApplicationProductType;
import com.ccapp.credit.application.domain.enums.CreditApplicationStatus;
import com.ccapp.credit.application.dto.CreateSeedCapitalCreditApplicationRequest;
import com.ccapp.credit.application.dto.CreditApplicationResponse;
import com.ccapp.credit.application.repository.CreditApplicationRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalBlockingReason;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalPendingCriterion;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalCreditCandidateResponse;
import com.ccapp.seedcapital.readiness.service.SeedCapitalCreditBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditApplicationService {

    private static final EnumSet<CreditApplicationStatus> OPEN_WORKFLOW_STATES = EnumSet.of(
            CreditApplicationStatus.SUBMITTED,
            CreditApplicationStatus.UNDER_REVIEW,
            CreditApplicationStatus.APPROVED,
            CreditApplicationStatus.WAITING_FUTURE_GATES,
            CreditApplicationStatus.BLOCKED
    );

    private final CreditApplicationRepository creditApplicationRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final SeedCapitalCreditBridgeService seedCapitalCreditBridgeService;

    @Transactional
    public CreditApplicationResponse createSeedCapitalApplication(UUID actorUserId,
                                                                 UUID requesterAccountHolderId,
                                                                 CreateSeedCapitalCreditApplicationRequest request) {
        accountHolderService.getById(requesterAccountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.TRANSACT);

        SeedCapitalCreditCandidateResponse candidate = seedCapitalCreditBridgeService.requireCandidate(
                requesterAccountHolderId,
                request.circleId()
        );
        if (creditApplicationRepository.existsByAccountHolderIdAndCircleIdAndProductTypeAndWorkflowStateIn(
                requesterAccountHolderId,
                request.circleId(),
                CreditApplicationProductType.SEED_CAPITAL,
                OPEN_WORKFLOW_STATES
        )) {
            throw new BusinessException("An active seed capital credit application already exists for this circle");
        }
        if (request.requestedAmount().compareTo(candidate.maxEligibleAmount()) > 0) {
            throw new BusinessException("Requested amount exceeds current max eligible amount");
        }

        CountryContext countryContext = countryContextService.resolve(requesterAccountHolderId);
        CreditApplication application = creditApplicationRepository.save(CreditApplication.builder()
                .accountHolderId(requesterAccountHolderId)
                .circleId(request.circleId())
                .productType(CreditApplicationProductType.SEED_CAPITAL)
                .bridgeState(candidate.state())
                .workflowState(CreditApplicationStatus.SUBMITTED)
                .requestedAmount(request.requestedAmount())
                .currency(countryContext.currency())
                .requestedDurationMonths(request.requestedDurationMonths())
                .requestedInterestRate(request.requestedInterestRate())
                .readinessState(candidate.readinessState())
                .readinessTrustScore(candidate.readinessTrustScore())
                .creditEligibilityScore(candidate.creditEligibilityScore())
                .maxEligibleAmount(candidate.maxEligibleAmount())
                .savingsGateState(candidate.savingsGateState())
                .configuredImfChannelsCount(candidate.configuredImfChannelsCount())
                .readyImfChannelsCount(candidate.readyImfChannelsCount())
                .qualifyingSavingsCyclesCount(candidate.qualifyingSavingsCyclesCount())
                .requiredSavingsCyclesCount(candidate.requiredSavingsCyclesCount())
                .blockingReasons(toCsv(candidate.readinessBlockingReasons()))
                .pendingCriteria(toCsv(candidate.readinessPendingCriteria()))
                .bridgeEvaluatedAt(candidate.evaluatedAt())
                .build());
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public List<CreditApplicationResponse> getMyApplications(UUID actorUserId, UUID requesterAccountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.VIEW);
        return creditApplicationRepository.findByAccountHolderIdOrderByCreatedAtDesc(requesterAccountHolderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CreditApplicationResponse getApplication(UUID actorUserId, UUID requesterAccountHolderId, UUID applicationId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.VIEW);
        CreditApplication application = creditApplicationRepository.findByIdAndAccountHolderId(applicationId, requesterAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Credit application not found"));
        return toResponse(application);
    }

    private CreditApplicationResponse toResponse(CreditApplication application) {
        return new CreditApplicationResponse(
                application.getId(),
                application.getAccountHolderId(),
                application.getCircleId(),
                application.getProductType(),
                application.getBridgeState(),
                application.getWorkflowState(),
                application.getRequestedAmount(),
                application.getCurrency(),
                application.getRequestedDurationMonths(),
                application.getRequestedInterestRate(),
                application.getReadinessState(),
                application.getReadinessTrustScore(),
                application.getCreditEligibilityScore(),
                application.getMaxEligibleAmount(),
                application.getSavingsGateState(),
                application.getConfiguredImfChannelsCount(),
                application.getReadyImfChannelsCount(),
                application.getQualifyingSavingsCyclesCount(),
                application.getRequiredSavingsCyclesCount(),
                parseCsv(application.getBlockingReasons(), SeedCapitalBlockingReason.class),
                parseCsv(application.getPendingCriteria(), SeedCapitalPendingCriterion.class),
                application.getLinkedLoanId(),
                application.getSubmittedAt(),
                application.getBridgeEvaluatedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    private String toCsv(Iterable<? extends Enum<?>> values) {
        StringBuilder builder = new StringBuilder();
        for (Enum<?> value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.name());
        }
        return builder.toString();
    }

    private <E extends Enum<E>> List<E> parseCsv(String csv, Class<E> enumType) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> Enum.valueOf(enumType, value))
                .toList();
    }
}
