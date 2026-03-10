package com.ccapp.capital.service;

import com.ccapp.admin.domain.enums.AdminPolicyType;
import com.ccapp.admin.repository.AdminPolicyRepository;
import com.ccapp.capital.domain.entity.CapitalPool;
import com.ccapp.capital.domain.entity.Fund;
import com.ccapp.capital.dto.AllocateCapitalPoolRequest;
import com.ccapp.capital.dto.CapitalPoolResponse;
import com.ccapp.capital.repository.CapitalPoolRepository;
import com.ccapp.capital.repository.FundRepository;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.service.CountryService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CapitalPoolService {

    private final CapitalPoolRepository capitalPoolRepository;
    private final FundRepository fundRepository;
    private final CircleRepository circleRepository;
    private final AccountHolderService accountHolderService;
    private final CountryService countryService;
    private final AdminPolicyRepository adminPolicyRepository;

    @Transactional
    public CapitalPoolResponse allocatePool(UUID accountHolderId, AllocateCapitalPoolRequest request) {
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new NotFoundException("Fund not found"));

        String countryCode = resolveAccountHolderCountryCode(accountHolderId);
        Circle circle = circleRepository.findByIdAndCountryCode(request.circleId(), countryCode)
                .orElseThrow(() -> new NotFoundException("Circle not found for account holder country"));

        if (fund.getAvailableCapital().compareTo(request.allocatedAmount()) < 0) {
            throw new BusinessException("Insufficient available capital in fund");
        }
        ensureSeedCapitalPolicyConfigured(countryCode);

        CapitalPool pool = capitalPoolRepository.findByFund_IdAndCircleIdAndCountryCode(request.fundId(), request.circleId(), countryCode)
                .orElseGet(() -> CapitalPool.builder()
                        .fund(fund)
                        .circleId(circle.getId())
                        .countryCode(countryCode)
                        .allocatedAmount(BigDecimal.ZERO)
                        .usedAmount(BigDecimal.ZERO)
                        .build());

        pool.setAllocatedAmount(pool.getAllocatedAmount().add(request.allocatedAmount()));

        fund.setAvailableCapital(fund.getAvailableCapital().subtract(request.allocatedAmount()));
        fundRepository.save(fund);

        CapitalPool saved = capitalPoolRepository.save(pool);
        return toResponse(saved);
    }

    @Transactional
    public CapitalPoolResponse allocatePool(AllocateCapitalPoolRequest request) {
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new NotFoundException("Fund not found"));
        Circle circle = circleRepository.findById(request.circleId())
                .orElseThrow(() -> new NotFoundException("Circle not found"));

        if (fund.getAvailableCapital().compareTo(request.allocatedAmount()) < 0) {
            throw new BusinessException("Insufficient available capital in fund");
        }
        ensureSeedCapitalPolicyConfigured(circle.getCountryCode());

        CapitalPool pool = capitalPoolRepository.findByFund_IdAndCircleIdAndCountryCode(request.fundId(), request.circleId(), circle.getCountryCode())
                .orElseGet(() -> CapitalPool.builder()
                        .fund(fund)
                        .circleId(circle.getId())
                        .countryCode(circle.getCountryCode())
                        .allocatedAmount(BigDecimal.ZERO)
                        .usedAmount(BigDecimal.ZERO)
                        .build());

        pool.setAllocatedAmount(pool.getAllocatedAmount().add(request.allocatedAmount()));

        fund.setAvailableCapital(fund.getAvailableCapital().subtract(request.allocatedAmount()));
        fundRepository.save(fund);

        CapitalPool saved = capitalPoolRepository.save(pool);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CapitalPoolResponse> listPools(UUID accountHolderId, UUID circleId) {
        String countryCode = resolveAccountHolderCountryCode(accountHolderId);
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

    private String resolveAccountHolderCountryCode(UUID accountHolderId) {
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        return countryService.resolveServiceCountryByCodeOrFallback(accountHolder.getServiceCountryCode()).getCode();
    }

    private void ensureSeedCapitalPolicyConfigured(String countryCode) {
        boolean hasPolicy = adminPolicyRepository.findFirstByPolicyTypeAndCountryCodeOrderByCreatedAtDesc(AdminPolicyType.SEED_CAPITAL_POLICY, countryCode).isPresent()
                || adminPolicyRepository.findFirstByPolicyTypeAndCountryCodeIsNullOrderByCreatedAtDesc(AdminPolicyType.SEED_CAPITAL_POLICY).isPresent();
        if (!hasPolicy) {
            throw new BusinessException("Seed capital policy not configured for country");
        }
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
