package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.InstitutionLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstitutionLedgerEntryRepository extends JpaRepository<InstitutionLedgerEntry, UUID> {

    Optional<InstitutionLedgerEntry> findByReferenceId(String referenceId);
}
