package com.ccapp.circles.service;

import com.ccapp.circles.dto.CircleCycleResponse;
import com.ccapp.circles.dto.CircleLoanResponse;
import com.ccapp.circles.dto.CircleRepaymentResponse;
import com.ccapp.circles.dto.CreateCircleCycleRequest;
import com.ccapp.circles.dto.CreateCircleLoanRequest;
import com.ccapp.circles.dto.CreateCircleRepaymentRequest;
import com.ccapp.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Deprecated(since = "2026-04", forRemoval = false)
public class CircleFinanceService {

    private static final String CIRCLE_FINANCE_DISABLED_FOR_MVP = "CIRCLE_FINANCE_DISABLED_FOR_MVP";

    @Transactional
    public CircleCycleResponse createCycle(UUID actorAccountHolderId, UUID circleId, CreateCircleCycleRequest request) {
        throw new BusinessException(CIRCLE_FINANCE_DISABLED_FOR_MVP);
    }

    @Transactional(readOnly = true)
    public List<CircleCycleResponse> getCycles(UUID circleId) {
        return List.of();
    }

    @Transactional
    public CircleLoanResponse createLoan(UUID actorAccountHolderId, UUID circleId, CreateCircleLoanRequest request) {
        throw new BusinessException(CIRCLE_FINANCE_DISABLED_FOR_MVP);
    }

    @Transactional
    public CircleRepaymentResponse createRepayment(UUID actorAccountHolderId, UUID loanId, CreateCircleRepaymentRequest request) {
        throw new BusinessException(CIRCLE_FINANCE_DISABLED_FOR_MVP);
    }
}
