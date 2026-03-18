package com.ccapp.financing.service;

import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.entity.OfferCountryConfig;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.domain.enums.PartnerBackofficeRole;
import com.ccapp.financing.domain.enums.PartnerLedgerEntryType;
import com.ccapp.financing.dto.CreateLoanApplicationRequest;
import com.ccapp.financing.dto.LoanApplicationResponse;
import com.ccapp.financing.dto.ReviewLoanApplicationRequest;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.LoanApplicationRepository;
import com.ccapp.financing.repository.OfferCountryConfigRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancingApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final LoanApplicationRepository loanApplicationRepository;
    private final FinancingOfferRepository financingOfferRepository;
    private final OfferCountryConfigRepository offerCountryConfigRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final CircleMemberRepository circleMemberRepository;
    private final PartnerBackofficeAccessService partnerBackofficeAccessService;
    private final PartnerLedgerService partnerLedgerService;
    private final PartnerReportingService partnerReportingService;
    private final OperationService operationService;
    private final AccountingPostingService accountingPostingService;
    private final CountryConfigResolver countryConfigResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public LoanApplicationResponse createApplication(UUID actorUserId,
                                                     UUID requesterAccountHolderId,
                                                     CreateLoanApplicationRequest request) {
        accountHolderService.getById(requesterAccountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.TRANSACT);

        FinancingOffer offer = loadOffer(request.offerId());
        if (offer.getStatus() != FinancingOfferStatus.ACTIVE) {
            throw new BusinessException("Offer is not active");
        }
        validateRequestedAmount(request.requestedAmount(), offer);
        CountryContext countryContext = countryContextService.resolve(requesterAccountHolderId);
        CountryConfigResolver.ResolvedConfig financingConfig = countryConfigResolver.resolveActiveConfig(
                countryContext.serviceCountryCode(),
                CountryConfigType.FINANCING
        );
        CountryConfigResolver.ResolvedConfig guaranteeConfig = countryConfigResolver.resolveActiveConfig(
                countryContext.serviceCountryCode(),
                CountryConfigType.GUARANTEE
        );
        validateCountryLevelFinancingConstraints(request.requestedAmount(), financingConfig, guaranteeConfig);

        String currency = resolveCreationCurrency(offer, requesterAccountHolderId);
        if (request.circleId() != null && !circleMemberRepository.existsByCircle_IdAndAccountHolderId(request.circleId(), requesterAccountHolderId)) {
            throw new BusinessException("Circle membership is required for this circle");
        }

        LoanApplication application = loanApplicationRepository.save(LoanApplication.builder()
                .offerId(offer.getId())
                .accountHolderId(requesterAccountHolderId)
                .circleId(request.circleId())
                .requestedAmount(request.requestedAmount())
                .status(LoanApplicationStatus.SUBMITTED)
                .configSnapshot(buildApplicationConfigSnapshot(countryContext.serviceCountryCode(), financingConfig, guaranteeConfig))
                .configVersion(financingConfig.version())
                .build());

        partnerLedgerService.recordEntry(
                offer.getPartnerId(),
                application.getId(),
                PartnerLedgerEntryType.APPLICATION_SUBMITTED,
                application.getRequestedAmount(),
                currency,
                "Financing application submitted"
        );
        partnerReportingService.refreshDailySnapshot(offer.getPartnerId());
        return toResponse(application, offer, currency);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanApplicationResponse> listApplications(UUID actorUserId,
                                                                  UUID requesterAccountHolderId,
                                                                  UUID partnerId,
                                                                  LoanApplicationStatus status,
                                                                  Integer page,
                                                                  Integer size) {
        Pageable pageable = normalizePageable(page, size);
        Page<LoanApplication> applicationPage;

        if (partnerId != null) {
            partnerBackofficeAccessService.assertHasAnyRole(
                    requesterAccountHolderId,
                    partnerId,
                    Set.of(PartnerBackofficeRole.PARTNER_ADMIN, PartnerBackofficeRole.PARTNER_ANALYST, PartnerBackofficeRole.PARTNER_VIEWER)
            );
            applicationPage = loanApplicationRepository.searchForPartner(partnerId, status, pageable);
        } else {
            accessControlService.assertCanActOnAccountHolder(actorUserId, requesterAccountHolderId, PermissionLevel.VIEW);
            applicationPage = loanApplicationRepository.searchForAccountHolder(requesterAccountHolderId, status, pageable);
        }

        Map<UUID, FinancingOffer> offersById = financingOfferRepository.findAllById(
                        applicationPage.getContent().stream().map(LoanApplication::getOfferId).toList()
                ).stream()
                .collect(Collectors.toMap(FinancingOffer::getId, Function.identity()));
        Page<LoanApplicationResponse> mappedPage = applicationPage.map(application -> {
            FinancingOffer offer = offersById.get(application.getOfferId());
            String currency = resolveApplicationCurrency(application, offer);
            return toResponse(application, offer, currency);
        });
        return PageResponse.from(mappedPage);
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse getApplication(UUID actorUserId,
                                                  UUID requesterAccountHolderId,
                                                  UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Loan application not found"));
        FinancingOffer offer = loadOffer(application.getOfferId());

        boolean canActAsOwner = accessControlService.canActOnAccountHolder(actorUserId, application.getAccountHolderId(), PermissionLevel.VIEW);
        boolean canActAsPartner = partnerBackofficeAccessService.canAccessPartner(requesterAccountHolderId, offer.getPartnerId());
        if (!canActAsOwner && !canActAsPartner) {
            throw new UnauthorizedException("Application access denied");
        }

        return toResponse(application, offer, resolveApplicationCurrency(application, offer));
    }

    @Transactional
    public LoanApplicationResponse reviewApplication(UUID reviewerAccountHolderId,
                                                     UUID applicationId,
                                                     ReviewLoanApplicationRequest request) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Loan application not found"));
        FinancingOffer offer = loadOffer(application.getOfferId());

        partnerBackofficeAccessService.assertHasAnyRole(
                reviewerAccountHolderId,
                offer.getPartnerId(),
                Set.of(PartnerBackofficeRole.PARTNER_ADMIN, PartnerBackofficeRole.PARTNER_ANALYST)
        );

        ensureAllowedTransition(application.getStatus(), request.status());
        if (request.partnerReference() != null && !request.partnerReference().isBlank()) {
            application.setPartnerReference(request.partnerReference().trim());
        }

        if (request.status() == LoanApplicationStatus.APPROVED
                || request.status() == LoanApplicationStatus.DISBURSED
                || request.status() == LoanApplicationStatus.REPAID) {
            BigDecimal approvedAmount = request.approvedAmount() != null
                    ? request.approvedAmount()
                    : application.getApprovedAmount();
            if (approvedAmount == null || approvedAmount.signum() <= 0) {
                approvedAmount = application.getRequestedAmount();
            }
            if (approvedAmount.compareTo(application.getRequestedAmount()) > 0) {
                throw new BusinessException("Approved amount cannot exceed requested amount");
            }
            application.setApprovedAmount(approvedAmount);
        }

        application.setStatus(request.status());
        LoanApplication saved = loanApplicationRepository.save(application);

        BigDecimal effectiveAmount = saved.getApprovedAmount() != null
                ? saved.getApprovedAmount()
                : saved.getRequestedAmount();
        String currency = resolveApplicationCurrency(saved, offer);
        switch (saved.getStatus()) {
            case APPROVED -> partnerLedgerService.recordEntry(
                    offer.getPartnerId(),
                    saved.getId(),
                    PartnerLedgerEntryType.APPLICATION_APPROVED,
                    effectiveAmount,
                    currency,
                    "Financing application approved"
            );
            case DISBURSED -> {
                partnerLedgerService.recordEntry(
                        offer.getPartnerId(),
                        saved.getId(),
                        PartnerLedgerEntryType.DISBURSEMENT,
                        effectiveAmount,
                        currency,
                        "Financing application disbursed"
                );
                postCoreLedger(saved, reviewerAccountHolderId, OperationType.LOAN_DISBURSEMENT, effectiveAmount, currency);
            }
            case REPAID -> {
                partnerLedgerService.recordEntry(
                        offer.getPartnerId(),
                        saved.getId(),
                        PartnerLedgerEntryType.REPAYMENT,
                        effectiveAmount,
                        currency,
                        "Financing application repaid"
                );
                postCoreLedger(saved, reviewerAccountHolderId, OperationType.LOAN_REPAYMENT, effectiveAmount, currency);
            }
            default -> {
            }
        }
        partnerReportingService.refreshDailySnapshot(offer.getPartnerId());
        return toResponse(saved, offer, currency);
    }

    private void postCoreLedger(LoanApplication application,
                                UUID actorUserId,
                                OperationType operationType,
                                BigDecimal amount,
                                String currency) {
        String operationCode = operationType.name().toLowerCase(Locale.ROOT);
        Operation operation = operationService.createInitiatedOperation(
                application.getAccountHolderId(),
                actorUserId,
                operationType,
                amount,
                currency,
                "financing-" + operationCode + ":" + application.getId(),
                "Financing application " + operationCode + " " + application.getId()
        );
        operationService.confirmOperation(operation.getId());
        accountingPostingService.postConfirmedOperation(operation.getId());
    }

    private void ensureAllowedTransition(LoanApplicationStatus currentStatus, LoanApplicationStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }
        boolean allowed = switch (currentStatus) {
            case DRAFT -> targetStatus == LoanApplicationStatus.SUBMITTED;
            case SUBMITTED -> targetStatus == LoanApplicationStatus.UNDER_REVIEW
                    || targetStatus == LoanApplicationStatus.APPROVED
                    || targetStatus == LoanApplicationStatus.REJECTED;
            case UNDER_REVIEW -> targetStatus == LoanApplicationStatus.APPROVED
                    || targetStatus == LoanApplicationStatus.REJECTED;
            case APPROVED -> targetStatus == LoanApplicationStatus.DISBURSED;
            case DISBURSED -> targetStatus == LoanApplicationStatus.REPAID;
            case REJECTED, REPAID -> false;
        };
        if (!allowed) {
            throw new BusinessException("Invalid application status transition");
        }
    }

    private void validateRequestedAmount(BigDecimal requestedAmount, FinancingOffer offer) {
        if (requestedAmount.compareTo(offer.getMinAmount()) < 0) {
            throw new BusinessException("Requested amount is below offer minimum");
        }
        if (requestedAmount.compareTo(offer.getMaxAmount()) > 0) {
            throw new BusinessException("Requested amount exceeds offer maximum");
        }
    }

    private void validateCountryLevelFinancingConstraints(BigDecimal requestedAmount,
                                                          CountryConfigResolver.ResolvedConfig financingConfig,
                                                          CountryConfigResolver.ResolvedConfig guaranteeConfig) {
        ObjectNode financingPayload = countryConfigResolver.payloadAsObjectNode(financingConfig);
        ObjectNode guaranteePayload = countryConfigResolver.payloadAsObjectNode(guaranteeConfig);

        BigDecimal minAmount = decimalField(financingPayload, "minAmount");
        BigDecimal maxAmount = decimalField(financingPayload, "maxAmount");
        BigDecimal maxExposure = decimalField(guaranteePayload, "globalMaxExposure");
        if (maxExposure == null) {
            maxExposure = maxTierGuaranteeAmount(guaranteePayload, "maxAmount");
        }
        if (maxExposure == null) {
            maxExposure = maxTierGuaranteeAmount(guaranteePayload, "maxExposure");
        }

        if (minAmount != null && requestedAmount.compareTo(minAmount) < 0) {
            throw new BusinessException("Requested amount is below country financing minimum");
        }
        if (maxAmount != null && requestedAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException("Requested amount exceeds country financing maximum");
        }
        if (maxExposure != null && requestedAmount.compareTo(maxExposure) > 0) {
            throw new BusinessException("Requested amount exceeds country guarantee limit");
        }
    }

    private FinancingOffer loadOffer(UUID offerId) {
        return financingOfferRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
    }

    private String buildApplicationConfigSnapshot(String countryCode,
                                                  CountryConfigResolver.ResolvedConfig financingConfig,
                                                  CountryConfigResolver.ResolvedConfig guaranteeConfig) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("countryCode", countryCode);
        root.put("financingConfigVersion", financingConfig.version());
        root.put("guaranteeConfigVersion", guaranteeConfig.version());
        root.set("financing", countryConfigResolver.payloadAsObjectNode(financingConfig));
        root.set("guarantee", countryConfigResolver.payloadAsObjectNode(guaranteeConfig));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Unable to serialize loan application configuration snapshot");
        }
    }

    private BigDecimal maxTierGuaranteeAmount(ObjectNode guaranteePayload, String fieldName) {
        if (guaranteePayload == null) {
            return null;
        }
        JsonNode limitsByTierNode = guaranteePayload.get("limitsByTier");
        if (limitsByTierNode == null || !limitsByTierNode.isArray()) {
            return null;
        }
        BigDecimal max = null;
        for (JsonNode limitNode : limitsByTierNode) {
            if (!limitNode.isObject()) {
                continue;
            }
            JsonNode value = limitNode.get(fieldName);
            if (value == null || !value.isNumber()) {
                continue;
            }
            BigDecimal amount = value.decimalValue();
            if (max == null || amount.compareTo(max) > 0) {
                max = amount;
            }
        }
        return max;
    }

    private BigDecimal decimalField(ObjectNode payload, String fieldName) {
        if (payload == null) {
            return null;
        }
        if (!payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        if (!payload.get(fieldName).isNumber()) {
            throw new BusinessException(fieldName + " must be numeric");
        }
        return payload.get(fieldName).decimalValue();
    }

    private String resolveCreationCurrency(FinancingOffer offer, UUID accountHolderId) {
        CountryContext countryContext = countryContextService.resolve(accountHolderId);
        List<OfferCountryConfig> configs = offerCountryConfigRepository.findByOfferIdOrderByCountryCodeAsc(offer.getId());
        if (configs.isEmpty()) {
            return offer.getCurrency();
        }

        OfferCountryConfig selected = configs.stream()
                .filter(config -> config.getCountryCode().equalsIgnoreCase(countryContext.serviceCountryCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Offer is not configured for this country"));
        if (!selected.isEnabled()) {
            throw new BusinessException("Offer is disabled for this country");
        }
        return selected.getCurrency();
    }

    private String resolveApplicationCurrency(LoanApplication application, FinancingOffer offer) {
        if (offer == null) {
            return null;
        }
        List<OfferCountryConfig> configs = offerCountryConfigRepository.findByOfferIdOrderByCountryCodeAsc(offer.getId());
        if (configs.isEmpty()) {
            return offer.getCurrency();
        }
        String serviceCountryCode = countryContextService.resolve(application.getAccountHolderId()).serviceCountryCode();
        return configs.stream()
                .filter(config -> config.getCountryCode().equalsIgnoreCase(serviceCountryCode))
                .map(OfferCountryConfig::getCurrency)
                .findFirst()
                .orElse(offer.getCurrency());
    }

    private LoanApplicationResponse toResponse(LoanApplication application, FinancingOffer offer, String currency) {
        return new LoanApplicationResponse(
                application.getId(),
                application.getOfferId(),
                offer.getPartnerId(),
                application.getAccountHolderId(),
                application.getCircleId(),
                application.getRequestedAmount(),
                application.getApprovedAmount(),
                currency,
                application.getStatus(),
                application.getPartnerReference(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    private Pageable normalizePageable(Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }
}
