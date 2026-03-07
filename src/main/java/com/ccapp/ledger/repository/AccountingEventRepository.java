package com.ccapp.ledger.repository;

import com.ccapp.ledger.domain.entity.AccountingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountingEventRepository extends JpaRepository<AccountingEvent, UUID> {
    Optional<AccountingEvent> findByOperationId(UUID operationId);
}
