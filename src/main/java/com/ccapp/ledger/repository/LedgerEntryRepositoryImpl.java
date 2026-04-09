package com.ccapp.ledger.repository;

import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Subgraph;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class LedgerEntryRepositoryImpl implements LedgerEntryRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<LedgerEntry> searchExplorer(String accountCode,
                                            OperationType operationType,
                                            OffsetDateTime fromDate,
                                            OffsetDateTime toDate,
                                            Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<LedgerEntry> contentQuery = criteriaBuilder.createQuery(LedgerEntry.class);
        Root<LedgerEntry> ledgerEntryRoot = contentQuery.from(LedgerEntry.class);
        Join<LedgerEntry, AccountingEvent> eventJoin = ledgerEntryRoot.join("event", JoinType.INNER);
        Join<AccountingEvent, Operation> operationJoin = eventJoin.join("operation", JoinType.INNER);

        List<Predicate> predicates = buildPredicates(
                criteriaBuilder,
                ledgerEntryRoot,
                eventJoin,
                operationJoin,
                accountCode,
                operationType,
                fromDate,
                toDate
        );

        contentQuery.select(ledgerEntryRoot)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(
                        criteriaBuilder.desc(eventJoin.get("postedAt")),
                        criteriaBuilder.asc(ledgerEntryRoot.get("lineNo"))
                );

        TypedQuery<LedgerEntry> typedQuery = entityManager.createQuery(contentQuery);
        typedQuery.setHint("jakarta.persistence.fetchgraph", ledgerEntryFetchGraph());
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<LedgerEntry> countRoot = countQuery.from(LedgerEntry.class);
        Join<LedgerEntry, AccountingEvent> countEventJoin = countRoot.join("event", JoinType.INNER);
        Join<AccountingEvent, Operation> countOperationJoin = countEventJoin.join("operation", JoinType.INNER);

        List<Predicate> countPredicates = buildPredicates(
                criteriaBuilder,
                countRoot,
                countEventJoin,
                countOperationJoin,
                accountCode,
                operationType,
                fromDate,
                toDate
        );

        countQuery.select(criteriaBuilder.count(countRoot))
                .where(countPredicates.toArray(Predicate[]::new));

        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private EntityGraph<LedgerEntry> ledgerEntryFetchGraph() {
        EntityGraph<LedgerEntry> entityGraph = entityManager.createEntityGraph(LedgerEntry.class);
        Subgraph<AccountingEvent> eventGraph = entityGraph.addSubgraph("event");
        eventGraph.addAttributeNodes("operation");
        return entityGraph;
    }

    private List<Predicate> buildPredicates(CriteriaBuilder criteriaBuilder,
                                            Root<LedgerEntry> ledgerEntryRoot,
                                            Join<LedgerEntry, AccountingEvent> eventJoin,
                                            Join<AccountingEvent, Operation> operationJoin,
                                            String accountCode,
                                            OperationType operationType,
                                            OffsetDateTime fromDate,
                                            OffsetDateTime toDate) {
        List<Predicate> predicates = new ArrayList<>();

        if (accountCode != null && !accountCode.isBlank()) {
            predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.upper(ledgerEntryRoot.get("accountCode")),
                    accountCode.trim().toUpperCase(Locale.ROOT)
            ));
        }
        if (operationType != null) {
            predicates.add(criteriaBuilder.equal(operationJoin.get("type"), operationType));
        }
        if (fromDate != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(eventJoin.get("postedAt"), fromDate));
        }
        if (toDate != null) {
            predicates.add(criteriaBuilder.lessThan(eventJoin.get("postedAt"), toDate));
        }
        return predicates;
    }
}
