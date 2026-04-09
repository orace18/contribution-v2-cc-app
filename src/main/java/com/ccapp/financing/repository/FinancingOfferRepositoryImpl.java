package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
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
public class FinancingOfferRepositoryImpl implements FinancingOfferRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<FinancingOffer> search(UUID partnerId,
                                       FinancingOfferStatus status,
                                       String countryCode,
                                       String search,
                                       Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<FinancingOffer> contentQuery = criteriaBuilder.createQuery(FinancingOffer.class);
        Root<FinancingOffer> root = contentQuery.from(FinancingOffer.class);
        List<Predicate> predicates = buildPredicates(criteriaBuilder, root, partnerId, status, countryCode, search);

        contentQuery.select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(criteriaBuilder.desc(root.get("createdAt")));

        TypedQuery<FinancingOffer> typedQuery = entityManager.createQuery(contentQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<FinancingOffer> countRoot = countQuery.from(FinancingOffer.class);
        List<Predicate> countPredicates = buildPredicates(criteriaBuilder, countRoot, partnerId, status, countryCode, search);

        countQuery.select(criteriaBuilder.count(countRoot))
                .where(countPredicates.toArray(Predicate[]::new));

        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder criteriaBuilder,
                                            Root<FinancingOffer> root,
                                            UUID partnerId,
                                            FinancingOfferStatus status,
                                            String countryCode,
                                            String search) {
        List<Predicate> predicates = new ArrayList<>();

        if (partnerId != null) {
            predicates.add(criteriaBuilder.equal(root.get("partnerId"), partnerId));
        }
        if (status != null) {
            predicates.add(criteriaBuilder.equal(root.get("status"), status));
        }
        if (countryCode != null && !countryCode.isBlank()) {
            predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.upper(root.get("countryCode")),
                    countryCode.trim().toUpperCase(Locale.ROOT)
            ));
        }
        if (search != null && !search.isBlank()) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + search.trim().toLowerCase(Locale.ROOT) + "%"
            ));
        }
        return predicates;
    }
}
