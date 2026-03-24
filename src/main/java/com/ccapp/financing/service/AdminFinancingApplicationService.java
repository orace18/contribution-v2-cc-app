package com.ccapp.financing.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.credit.domain.entity.CreditEligibility;
import com.ccapp.credit.repository.CreditEligibilityRepository;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.dto.AdminFinancingApplicationApplicantResponse;
import com.ccapp.financing.dto.AdminFinancingApplicationDetailsResponse;
import com.ccapp.financing.dto.AdminFinancingApplicationDocumentResponse;
import com.ccapp.financing.dto.AdminFinancingApplicationListItemResponse;
import com.ccapp.financing.dto.AdminFinancingApplicationScoringResponse;
import com.ccapp.financing.dto.LoanApplicationResponse;
import com.ccapp.financing.dto.ReviewLoanApplicationRequest;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.LoanApplicationRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminFinancingApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final LoanApplicationRepository loanApplicationRepository;
    private final FinancingOfferRepository financingOfferRepository;
    private final FinancialPartnerRepository financialPartnerRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final CreditEligibilityRepository creditEligibilityRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final FinancingApplicationService financingApplicationService;

    @Transactional(readOnly = true)
    public PageResponse<AdminFinancingApplicationListItemResponse> listApplications(String countryCode,
                                                                                    UUID partnerId,
                                                                                    LoanApplicationStatus status,
                                                                                    Integer page,
                                                                                    Integer size) {
        Pageable pageable = normalizePageable(page, size);
        Page<LoanApplication> applicationPage = loanApplicationRepository.searchForAdmin(
                partnerId,
                status,
                normalizeNullableCountry(countryCode),
                pageable
        );
        Map<UUID, FinancingOffer> offersById = financingOfferRepository.findAllById(
                        applicationPage.getContent().stream().map(LoanApplication::getOfferId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(FinancingOffer::getId, Function.identity()));
        Map<UUID, FinancialPartner> partnersById = financialPartnerRepository.findAllById(
                        offersById.values().stream().map(FinancingOffer::getPartnerId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(FinancialPartner::getId, Function.identity()));
        Map<UUID, AccountHolder> holdersById = accountHolderRepository.findAllById(
                        applicationPage.getContent().stream().map(LoanApplication::getAccountHolderId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(AccountHolder::getId, Function.identity()));

        return new PageResponse<>(
                applicationPage.getContent().stream()
                        .map(application -> toListItem(
                                application,
                                offersById.get(application.getOfferId()),
                                partnersById,
                                holdersById
                        ))
                        .toList(),
                applicationPage.getNumber(),
                applicationPage.getSize(),
                applicationPage.getTotalElements(),
                applicationPage.getTotalPages(),
                applicationPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminFinancingApplicationDetailsResponse getApplication(UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Loan application not found"));
        FinancingOffer offer = financingOfferRepository.findById(application.getOfferId())
                .orElseThrow(() -> new NotFoundException("Financing offer not found"));
        FinancialPartner partner = financialPartnerRepository.findById(offer.getPartnerId())
                .orElseThrow(() -> new NotFoundException("Financial partner not found"));
        AccountHolder applicant = accountHolderRepository.findById(application.getAccountHolderId())
                .orElseThrow(() -> new NotFoundException("Account holder not found"));

        CreditEligibility scoring = creditEligibilityRepository.findById(application.getAccountHolderId()).orElse(null);
        List<AdminFinancingApplicationDocumentResponse> documents = kycDocumentRepository.findByAccountHolderId(application.getAccountHolderId())
                .stream()
                .map(this::toDocument)
                .toList();

        return new AdminFinancingApplicationDetailsResponse(
                application.getId(),
                partner.getId(),
                partner.getName(),
                offer.getId(),
                offer.getName(),
                offer.getType(),
                offer.getCountryCode(),
                toApplicant(applicant),
                application.getRequestedAmount(),
                application.getApprovedAmount(),
                offer.getCurrency(),
                application.getStatus(),
                application.getPartnerReference(),
                application.getDecisionNote(),
                application.getConfigSnapshot(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                toScoring(scoring),
                documents
        );
    }

    @Transactional
    public AdminFinancingApplicationDetailsResponse reviewApplication(UUID reviewerActorUserId,
                                                                      UUID applicationId,
                                                                      ReviewLoanApplicationRequest request) {
        LoanApplicationResponse ignored = financingApplicationService.adminReviewApplication(reviewerActorUserId, applicationId, request);
        return getApplication(applicationId);
    }

    private AdminFinancingApplicationListItemResponse toListItem(LoanApplication application,
                                                                 FinancingOffer offer,
                                                                 Map<UUID, FinancialPartner> partnersById,
                                                                 Map<UUID, AccountHolder> holdersById) {
        FinancialPartner partner = offer == null ? null : partnersById.get(offer.getPartnerId());
        AccountHolder holder = holdersById.get(application.getAccountHolderId());
        return new AdminFinancingApplicationListItemResponse(
                application.getId(),
                partner == null ? null : partner.getId(),
                partner == null ? "Unknown partner" : partner.getName(),
                application.getOfferId(),
                offer == null ? "Unknown offer" : offer.getName(),
                application.getAccountHolderId(),
                holder == null ? "Unknown applicant" : buildFullName(holder),
                holder == null ? null : holder.getPhoneNumber(),
                offer == null ? null : offer.getCountryCode(),
                application.getRequestedAmount(),
                application.getApprovedAmount(),
                offer == null ? null : offer.getCurrency(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    private AdminFinancingApplicationApplicantResponse toApplicant(AccountHolder applicant) {
        return new AdminFinancingApplicationApplicantResponse(
                applicant.getId(),
                applicant.getFirstName(),
                applicant.getLastName(),
                buildFullName(applicant),
                applicant.getPhoneNumber(),
                applicant.getCountryCode(),
                applicant.getServiceCountryCode(),
                applicant.getKycStatus()
        );
    }

    private AdminFinancingApplicationScoringResponse toScoring(CreditEligibility scoring) {
        if (scoring == null) {
            return null;
        }
        return new AdminFinancingApplicationScoringResponse(
                scoring.getCircleId(),
                scoring.getRmsScore(),
                scoring.getCircleScore(),
                scoring.getEligibilityScore(),
                scoring.getMaxEligibleAmount(),
                scoring.getEvaluatedAt()
        );
    }

    private AdminFinancingApplicationDocumentResponse toDocument(KycDocument document) {
        return new AdminFinancingApplicationDocumentResponse(
                document.getId(),
                document.getMediaFileId(),
                document.getDocumentType(),
                document.getStatus(),
                document.getCreatedAt()
        );
    }

    private String buildFullName(AccountHolder holder) {
        return (holder.getFirstName() + " " + holder.getLastName()).trim();
    }

    private Pageable normalizePageable(Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    private String normalizeNullableCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
