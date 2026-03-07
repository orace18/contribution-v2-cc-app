package com.ccapp.admin.service;

import com.ccapp.admin.dto.*;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTreasuryService {

    private final AccountBalanceSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public List<TreasuryAccountResponse> getAccounts(String currency) {
        List<AccountBalanceSnapshot> snapshots = (currency == null || currency.isBlank())
                ? snapshotRepository.findAllByOrderByAccountCodeAsc()
                : snapshotRepository.findByCurrencyOrderByAccountCodeAsc(currency.trim().toUpperCase(Locale.ROOT));

        return snapshots.stream()
                .map(snapshot -> new TreasuryAccountResponse(
                        snapshot.getAccountCode(),
                        snapshot.getCurrency(),
                        snapshot.getBalance(),
                        snapshot.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional
    public TreasuryOperationResponse transfer(TreasuryTransferRequest request) {
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        if (request.sourceAccountCode().equalsIgnoreCase(request.destinationAccountCode())) {
            throw new BusinessException("Source and destination accounts must be different");
        }

        AccountBalanceSnapshot source = getOrCreateSnapshot(request.sourceAccountCode(), currency);
        AccountBalanceSnapshot destination = getOrCreateSnapshot(request.destinationAccountCode(), currency);

        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException("Insufficient source treasury balance");
        }

        source.setBalance(source.getBalance().subtract(request.amount()));
        destination.setBalance(destination.getBalance().add(request.amount()));

        snapshotRepository.save(source);
        snapshotRepository.save(destination);

        UUID operationId = UUID.randomUUID();
        return new TreasuryOperationResponse(
                operationId,
                "TRANSFER",
                "Treasury transfer processed",
                OffsetDateTime.now()
        );
    }

    @Transactional
    public TreasuryOperationResponse cashout(TreasuryCashoutRequest request) {
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        AccountBalanceSnapshot source = getOrCreateSnapshot(request.accountCode(), currency);

        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException("Insufficient treasury balance for cashout");
        }

        source.setBalance(source.getBalance().subtract(request.amount()));
        snapshotRepository.save(source);

        UUID operationId = UUID.randomUUID();
        return new TreasuryOperationResponse(
                operationId,
                "CASHOUT",
                "Treasury cashout processed",
                OffsetDateTime.now()
        );
    }

    private AccountBalanceSnapshot getOrCreateSnapshot(String accountCode, String currency) {
        return snapshotRepository.findByAccountCodeAndCurrency(accountCode.trim(), currency)
                .orElseGet(() -> snapshotRepository.save(AccountBalanceSnapshot.builder()
                        .accountCode(accountCode.trim())
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .version(0L)
                        .build()));
    }
}
