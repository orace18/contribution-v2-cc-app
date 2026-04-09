package com.ccapp.capital.service;

import com.ccapp.capital.domain.entity.Fund;
import com.ccapp.capital.dto.CreateFundRequest;
import com.ccapp.capital.dto.FundResponse;
import com.ccapp.capital.repository.FundRepository;
import com.ccapp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Deprecated(since = "2026-04", forRemoval = false)
public class FundService {

    private static final String LEGACY_CAPITAL_MODULE_DISABLED = "LEGACY_CAPITAL_MODULE_DISABLED";

    private final FundRepository fundRepository;

    @Transactional
    public FundResponse createFund(CreateFundRequest request) {
        throw new BusinessException(LEGACY_CAPITAL_MODULE_DISABLED);
    }

    @Transactional(readOnly = true)
    public List<FundResponse> listFunds(String currency) {
        if (currency != null && !currency.isBlank()) {
            return fundRepository.findByCurrencyOrderByCreatedAtDesc(currency.toUpperCase(Locale.ROOT)).stream()
                    .map(this::toResponse)
                    .toList();
        }

        return fundRepository.findAll().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    FundResponse toResponse(Fund fund) {
        return new FundResponse(
                fund.getId(),
                fund.getName(),
                fund.getProvider(),
                fund.getTotalCapital(),
                fund.getAvailableCapital(),
                fund.getCurrency(),
                fund.getCreatedAt()
        );
    }
}
