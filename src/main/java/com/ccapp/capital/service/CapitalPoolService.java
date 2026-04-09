package com.ccapp.capital.service;

import com.ccapp.capital.domain.entity.CapitalPool;
import com.ccapp.capital.dto.AllocateCapitalPoolRequest;
import com.ccapp.capital.dto.CapitalPoolResponse;
import com.ccapp.capital.repository.CapitalPoolRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Deprecated(since = "2026-04", forRemoval = false)
public class CapitalPoolService {

    private static final String LEGACY_CAPITAL_MODULE_DISABLED = "LEGACY_CAPITAL_MODULE_DISABLED";

    private final CapitalPoolRepository capitalPoolRepository;
    private final CountryContextService countryContextService;

    @Transactional
    public CapitalPoolResponse allocatePool(UUID accountHolderId, AllocateCapitalPoolRequest request) {
        throw new BusinessException(LEGACY_CAPITAL_MODULE_DISABLED);
    }

    @Transactional
    public CapitalPoolResponse allocatePool(AllocateCapitalPoolRequest request) {
        throw new BusinessException(LEGACY_CAPITAL_MODULE_DISABLED);
    }

    @Transactional(readOnly = true)
    public List<CapitalPoolResponse> listPools(UUID accountHolderId, UUID circleId) {
        CountryContext countryContext = countryContextService.resolve(accountHolderId);
        String countryCode = countryContext.serviceCountryCode();
        List<CapitalPool> pools = circleId == null
                ? capitalPoolRepository.findByCountryCodeOrderByIdDesc(countryCode)
                : capitalPoolRepository.findByCircleIdAndCountryCodeOrderByIdDesc(circleId, countryCode);

        return pools.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CapitalPoolResponse> listPools(UUID circleId) {
        List<CapitalPool> pools = circleId == null
                ? capitalPoolRepository.findAll()
                : capitalPoolRepository.findByCircleIdOrderByIdDesc(circleId);
        return pools.stream().map(this::toResponse).toList();
    }

    CapitalPoolResponse toResponse(CapitalPool pool) {
        BigDecimal remaining = pool.getAllocatedAmount().subtract(pool.getUsedAmount());
        return new CapitalPoolResponse(
                pool.getId(),
                pool.getFund().getId(),
                pool.getCircleId(),
                pool.getAllocatedAmount(),
                pool.getUsedAmount(),
                remaining
        );
    }
}
