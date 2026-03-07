package com.ccapp.ledger.repository;

import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceSnapshotRepository extends JpaRepository<AccountBalanceSnapshot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountBalanceSnapshot> findByAccountCodeAndCurrency(String accountCode, String currency);

    List<AccountBalanceSnapshot> findByCurrencyOrderByAccountCodeAsc(String currency);

    List<AccountBalanceSnapshot> findAllByOrderByAccountCodeAsc();
}
