package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FinancingOfferRepository extends JpaRepository<FinancingOffer, UUID> {

    List<FinancingOffer> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);

    List<FinancingOffer> findByStatusOrderByCreatedAtDesc(FinancingOfferStatus status);

    @Query("""
            select fo
            from FinancingOffer fo
            where (:partnerId is null or fo.partnerId = :partnerId)
              and (:status is null or fo.status = :status)
              and (:countryCode is null or upper(fo.countryCode) = upper(:countryCode))
              and (:search is null or lower(fo.name) like concat('%', :search, '%'))
            order by fo.createdAt desc
            """)
    Page<FinancingOffer> search(UUID partnerId,
                                FinancingOfferStatus status,
                                String countryCode,
                                String search,
                                Pageable pageable);

    List<FinancingOffer> findByCountryCodeAndStatusOrderByCreatedAtDesc(String countryCode, FinancingOfferStatus status);
}
