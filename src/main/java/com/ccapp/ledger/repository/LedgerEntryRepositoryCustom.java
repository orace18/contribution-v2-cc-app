package com.ccapp.ledger.repository;

import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.payments.operation.domain.enums.OperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;

public interface LedgerEntryRepositoryCustom {

    Page<LedgerEntry> searchExplorer(String accountCode,
                                     OperationType operationType,
                                     OffsetDateTime fromDate,
                                     OffsetDateTime toDate,
                                     Pageable pageable);
}
