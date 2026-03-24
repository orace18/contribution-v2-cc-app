package com.ccapp.ledger.repository;

import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.payments.operation.domain.enums.OperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountCodeOrderByCreatedAtAsc(String accountCode);

    List<LedgerEntry> findByAccountCodeAndCurrencyOrderByCreatedAtAsc(String accountCode, String currency);

    @Query(value = """
            select le
            from LedgerEntry le
            join fetch le.event event
            join fetch event.operation operation
            where (:accountCode is null or upper(le.accountCode) = upper(:accountCode))
              and (:operationType is null or operation.type = :operationType)
              and (:fromDate is null or event.postedAt >= :fromDate)
              and (:toDate is null or event.postedAt < :toDate)
            order by event.postedAt desc, le.lineNo asc
            """,
            countQuery = """
            select count(le)
            from LedgerEntry le
            join le.event event
            join event.operation operation
            where (:accountCode is null or upper(le.accountCode) = upper(:accountCode))
              and (:operationType is null or operation.type = :operationType)
              and (:fromDate is null or event.postedAt >= :fromDate)
              and (:toDate is null or event.postedAt < :toDate)
            """)
    Page<LedgerEntry> searchExplorer(String accountCode,
                                     OperationType operationType,
                                     OffsetDateTime fromDate,
                                     OffsetDateTime toDate,
                                     Pageable pageable);
}
