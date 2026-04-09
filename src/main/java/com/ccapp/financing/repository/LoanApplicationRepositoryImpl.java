package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class LoanApplicationRepositoryImpl implements LoanApplicationRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<LoanApplication> searchForAdmin(UUID partnerId,
                                                LoanApplicationStatus status,
                                                String countryCode,
                                                Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<LoanApplication> contentQuery = criteriaBuilder.createQuery(LoanApplication.class);
        Root<LoanApplication> applicationRoot = contentQuery.from(LoanApplication.class);
        Root<FinancingOffer> offerRoot = contentQuery.from(FinancingOffer.class);
        List<Predicate> predicates = buildAdminPredicates(
                criteriaBuilder,
                applicationRoot,
                offerRoot,
                partnerId,
                status,
                countryCode
        );

        contentQuery.select(applicationRoot)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(criteriaBuilder.desc(applicationRoot.get("createdAt")));

        TypedQuery<LoanApplication> typedQuery = entityManager.createQuery(contentQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<LoanApplication> countApplicationRoot = countQuery.from(LoanApplication.class);
        Root<FinancingOffer> countOfferRoot = countQuery.from(FinancingOffer.class);
        List<Predicate> countPredicates = buildAdminPredicates(
                criteriaBuilder,
                countApplicationRoot,
                countOfferRoot,
                partnerId,
                status,
                countryCode
        );

        countQuery.select(criteriaBuilder.count(countApplicationRoot))
                .where(countPredicates.toArray(Predicate[]::new));

        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> buildAdminPredicates(CriteriaBuilder criteriaBuilder,
                                                 Root<LoanApplication> applicationRoot,
                                                 Root<FinancingOffer> offerRoot,
                                                 UUID partnerId,
                                                 LoanApplicationStatus status,
                                                 String countryCode) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(offerRoot.get("id"), applicationRoot.get("offerId")));

        if (partnerId != null) {
            predicates.add(criteriaBuilder.equal(offerRoot.get("partnerId"), partnerId));
        }
        if (status != null) {
            predicates.add(criteriaBuilder.equal(applicationRoot.get("status"), status));
        }
        if (countryCode != null && !countryCode.isBlank()) {
            predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.upper(offerRoot.get("countryCode")),
                    countryCode.trim().toUpperCase(Locale.ROOT)
            ));
        }
        return predicates;
    }
}
