package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminLedgerEntryResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import com.ccapp.payments.operation.domain.enums.OperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AdminLedgerExplorerService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminLedgerEntryResponse> search(String accountCode,
                                                         OperationType operationType,
                                                         LocalDate fromDate,
                                                         LocalDate toDate,
                                                         Integer page,
                                                         Integer size) {
        Pageable pageable = normalizePageable(page, size);
        Page<LedgerEntry> ledgerPage = ledgerEntryRepository.searchExplorer(
                normalizeNullable(accountCode),
                operationType,
                fromDate == null ? null : fromDate.atStartOfDay().atOffset(ZoneOffset.UTC),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC),
                pageable
        );
        return PageResponse.from(ledgerPage.map(this::toResponse));
    }

    private AdminLedgerEntryResponse toResponse(LedgerEntry entry) {
        return new AdminLedgerEntryResponse(
                entry.getId(),
                entry.getEvent().getId(),
                entry.getEvent().getOperation().getId(),
                entry.getEvent().getOperation().getType(),
                entry.getAccountCode(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getDescription(),
                entry.getEvent().getPostedAt(),
                entry.getCreatedAt()
        );
    }

    private Pageable normalizePageable(Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
