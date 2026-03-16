package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.OfferRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfferRequirementRepository extends JpaRepository<OfferRequirement, UUID> {

    List<OfferRequirement> findByOfferIdOrderByRequirementTypeAsc(UUID offerId);

    void deleteByOfferId(UUID offerId);
}
