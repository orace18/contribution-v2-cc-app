package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancingOfferRepository extends JpaRepository<FinancingOffer, UUID>, FinancingOfferRepositoryCustom {

    List<FinancingOffer> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);

    List<FinancingOffer> findByStatusOrderByCreatedAtDesc(FinancingOfferStatus status);

    List<FinancingOffer> findByCountryCodeAndStatusOrderByCreatedAtDesc(String countryCode, FinancingOfferStatus status);
}
