package com.ccapp.ledger.repository;

import com.ccapp.ledger.domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, LedgerEntryRepositoryCustom {

    List<LedgerEntry> findByAccountCodeOrderByCreatedAtAsc(String accountCode);

    List<LedgerEntry> findByAccountCodeAndCurrencyOrderByCreatedAtAsc(String accountCode, String currency);
}
