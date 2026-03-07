package com.ccapp.capital.service;

import com.ccapp.capital.domain.entity.CapitalPool;
import com.ccapp.capital.domain.entity.Fund;
import com.ccapp.capital.dto.AllocateCapitalPoolRequest;
import com.ccapp.capital.dto.CapitalPoolResponse;
import com.ccapp.capital.repository.CapitalPoolRepository;
import com.ccapp.capital.repository.FundRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
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

    @Transactional
    public CapitalPoolResponse allocatePool(AllocateCapitalPoolRequest request) {
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new NotFoundException("Fund not found"));

        if (!circleRepository.existsById(request.circleId())) {
            throw new NotFoundException("Circle not found");
        }

        if (fund.getAvailableCapital().compareTo(request.allocatedAmount()) < 0) {
            throw new BusinessException("Insufficient available capital in fund");
        }

        CapitalPool pool = capitalPoolRepository.findByFund_IdAndCircleId(request.fundId(), request.circleId())
                .orElseGet(() -> CapitalPool.builder()
                        .fund(fund)
                        .circleId(request.circleId())
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
