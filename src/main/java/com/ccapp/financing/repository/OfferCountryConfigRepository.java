package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.OfferCountryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferCountryConfigRepository extends JpaRepository<OfferCountryConfig, UUID> {

    List<OfferCountryConfig> findByOfferIdOrderByCountryCodeAsc(UUID offerId);

    Optional<OfferCountryConfig> findByOfferIdAndCountryCode(UUID offerId, String countryCode);

    void deleteByOfferId(UUID offerId);
}
