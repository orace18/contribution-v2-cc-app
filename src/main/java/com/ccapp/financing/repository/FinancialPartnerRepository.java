package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialPartnerRepository extends JpaRepository<FinancialPartner, UUID>, JpaSpecificationExecutor<FinancialPartner> {

    List<FinancialPartner> findByCountryCodeOrderByNameAsc(String countryCode);

    List<FinancialPartner> findByStatusOrderByNameAsc(FinancialPartnerStatus status);

    Optional<FinancialPartner> findByNameIgnoreCaseAndCountryCodeIgnoreCase(String name, String countryCode);

    Optional<FinancialPartner> findByIdAndCountryCode(UUID id, String countryCode);
}
