package com.ccapp.accounting.ledger.repository;

import com.ccapp.accounting.ledger.domain.entity.AccountingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountingEventRepository extends JpaRepository<AccountingEvent, UUID> {
    Optional<AccountingEvent> findByOperationId(UUID operationId);
}
