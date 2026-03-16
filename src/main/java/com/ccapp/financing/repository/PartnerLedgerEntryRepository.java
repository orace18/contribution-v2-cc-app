package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.PartnerLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerLedgerEntryRepository extends JpaRepository<PartnerLedgerEntry, UUID> {

    Page<PartnerLedgerEntry> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId, Pageable pageable);

    List<PartnerLedgerEntry> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
