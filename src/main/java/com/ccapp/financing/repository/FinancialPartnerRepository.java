package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialPartnerRepository extends JpaRepository<FinancialPartner, UUID> {

    List<FinancialPartner> findByCountryCodeOrderByNameAsc(String countryCode);

    List<FinancialPartner> findByStatusOrderByNameAsc(FinancialPartnerStatus status);

    Optional<FinancialPartner> findByNameIgnoreCaseAndCountryCodeIgnoreCase(String name, String countryCode);

    @Query("""
            select fp
            from FinancialPartner fp
            where (:status is null or fp.status = :status)
              and (:type is null or fp.type = :type)
              and (:countryCode is null or upper(fp.countryCode) = upper(:countryCode))
              and (:search is null or lower(fp.name) like concat('%', :search, '%'))
            order by fp.createdAt desc
            """)
    Page<FinancialPartner> search(FinancialPartnerStatus status,
                                  FinancialPartnerType type,
                                  String countryCode,
                                  String search,
                                  Pageable pageable);
}
