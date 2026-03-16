package com.ccapp.financing.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.financing.dto.OfferRequirementResponse;
import com.ccapp.financing.repository.FinancingOfferRepository;
import com.ccapp.financing.repository.OfferRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferRequirementService {

    private final FinancingOfferRepository financingOfferRepository;
    private final OfferRequirementRepository offerRequirementRepository;

    @Transactional(readOnly = true)
    public List<OfferRequirementResponse> listRequirements(UUID offerId) {
        if (financingOfferRepository.findById(offerId).isEmpty()) {
            throw new NotFoundException("Financing offer not found");
        }
        return offerRequirementRepository.findByOfferIdOrderByRequirementTypeAsc(offerId).stream()
                .map(requirement -> new OfferRequirementResponse(
                        requirement.getId(),
                        requirement.getOfferId(),
                        requirement.getRequirementType(),
                        requirement.isRequired(),
                        requirement.getVerificationType(),
                        requirement.getCreatedAt()
                ))
                .toList();
    }
}
