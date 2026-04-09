package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID>, LoanApplicationRepositoryCustom {

    Page<LoanApplication> findByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId, Pageable pageable);

    long countByAccountHolderIdAndStatusIn(UUID accountHolderId, Collection<LoanApplicationStatus> statuses);

    @Query("""
            select la
            from LoanApplication la
            join FinancingOffer fo on fo.id = la.offerId
            where fo.partnerId in :partnerIds
            """)
    Page<LoanApplication> findByPartnerIds(Collection<UUID> partnerIds, Pageable pageable);

    @Query("""
            select la
            from LoanApplication la
            where la.accountHolderId = :accountHolderId
              and (:status is null or la.status = :status)
            order by la.createdAt desc
            """)
    Page<LoanApplication> searchForAccountHolder(UUID accountHolderId,
                                                 LoanApplicationStatus status,
                                                 Pageable pageable);

    @Query("""
            select la
            from LoanApplication la
            where la.offerId in (
                select fo.id
                from FinancingOffer fo
                where fo.partnerId = :partnerId
            )
              and (:status is null or la.status = :status)
            order by la.createdAt desc
            """)
    Page<LoanApplication> searchForPartner(UUID partnerId,
                                           LoanApplicationStatus status,
                                           Pageable pageable);

    @Query("""
            select la
            from LoanApplication la
            join FinancingOffer fo on fo.id = la.offerId
            where fo.partnerId = :partnerId
            order by la.createdAt desc
            """)
    List<LoanApplication> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);

    @Query("""
            select la
            from LoanApplication la
            join FinancingOffer fo on fo.id = la.offerId
            where fo.partnerId = :partnerId
              and la.status = :status
            order by la.createdAt asc
            """)
    List<LoanApplication> findByPartnerIdAndStatusOrderByCreatedAtAsc(UUID partnerId, LoanApplicationStatus status);

    @Query("""
            select la
            from LoanApplication la
            join FinancingOffer fo on fo.id = la.offerId
            where fo.partnerId = :partnerId
              and la.createdAt >= :start
              and la.createdAt <= :end
            """)
    List<LoanApplication> findByPartnerIdAndCreatedAtBetween(UUID partnerId, OffsetDateTime start, OffsetDateTime end);

    @Query("""
            select la
            from LoanApplication la
            join FinancingOffer fo on fo.id = la.offerId
            where fo.partnerId = :partnerId
            """)
    List<LoanApplication> findByPartnerId(UUID partnerId);

    @Query("""
            select la
            from LoanApplication la
            where la.id = :applicationId
              and la.offerId in (
                  select fo.id
                  from FinancingOffer fo
                  where fo.partnerId = :partnerId
              )
            """)
    Optional<LoanApplication> findByIdForPartner(UUID applicationId, UUID partnerId);
}
