package com.ccapp.financing.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.financing.domain.entity.InstitutionLedgerEntry;
import com.ccapp.financing.domain.enums.InstitutionLedgerEntryType;
import com.ccapp.financing.repository.InstitutionLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstitutionLedgerService {

    private final InstitutionLedgerEntryRepository institutionLedgerEntryRepository;

    @Transactional
    public InstitutionLedgerEntry recordEntry(UUID accountHolderId,
                                              UUID institutionId,
                                              InstitutionLedgerEntryType entryType,
                                              BigDecimal amount,
                                              String currency,
                                              String referenceId) {
        if (accountHolderId == null) {
            throw new BusinessException("Account holder is required");
        }
        if (institutionId == null) {
            throw new BusinessException("Institution is required");
        }
        if (entryType == null) {
            throw new BusinessException("Institution ledger entry type is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Institution ledger amount must be positive");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new BusinessException("Institution ledger reference is required");
        }

        return institutionLedgerEntryRepository.findByReferenceId(referenceId.trim())
                .orElseGet(() -> institutionLedgerEntryRepository.save(InstitutionLedgerEntry.builder()
                        .accountHolderId(accountHolderId)
                        .institutionId(institutionId)
                        .entryType(entryType)
                        .amount(amount)
                        .currency(normalizeCurrency(currency))
                        .referenceId(referenceId.trim())
                        .build()));
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessException("Currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
