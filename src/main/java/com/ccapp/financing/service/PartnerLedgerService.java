package com.ccapp.financing.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financing.domain.entity.PartnerLedgerAccount;
import com.ccapp.financing.domain.entity.PartnerLedgerEntry;
import com.ccapp.financing.domain.enums.PartnerLedgerEntryType;
import com.ccapp.financing.repository.PartnerLedgerAccountRepository;
import com.ccapp.financing.repository.PartnerLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerLedgerService {

    private final PartnerLedgerAccountRepository partnerLedgerAccountRepository;
    private final PartnerLedgerEntryRepository partnerLedgerEntryRepository;

    @Transactional
    public PartnerLedgerEntry recordEntry(UUID partnerId,
                                          UUID applicationId,
                                          PartnerLedgerEntryType entryType,
                                          BigDecimal amount,
                                          String currency,
                                          String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Ledger entry amount must be positive");
        }
        String normalizedCurrency = normalizeCurrency(currency);

        PartnerLedgerAccount account = partnerLedgerAccountRepository
                .findByPartnerIdAndCurrency(partnerId, normalizedCurrency)
                .orElseGet(() -> partnerLedgerAccountRepository.save(PartnerLedgerAccount.builder()
                        .partnerId(partnerId)
                        .currency(normalizedCurrency)
                        .balance(BigDecimal.ZERO)
                        .build()));

        account.setBalance(account.getBalance().add(resolveDelta(entryType, amount)));
        PartnerLedgerAccount savedAccount = partnerLedgerAccountRepository.save(account);

        return partnerLedgerEntryRepository.save(PartnerLedgerEntry.builder()
                .partnerId(partnerId)
                .partnerLedgerAccountId(savedAccount.getId())
                .applicationId(applicationId)
                .entryType(entryType)
                .amount(amount)
                .currency(normalizedCurrency)
                .description(description)
                .build());
    }

    private BigDecimal resolveDelta(PartnerLedgerEntryType entryType, BigDecimal amount) {
        return switch (entryType) {
            case DISBURSEMENT -> amount.negate();
            case REPAYMENT, SETTLEMENT -> amount;
            case APPLICATION_SUBMITTED, APPLICATION_APPROVED -> BigDecimal.ZERO;
        };
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
