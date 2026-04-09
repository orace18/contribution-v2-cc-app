package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminDashboardResponse;
import com.ccapp.admin.dto.SubscriptionStatsResponse;
import com.ccapp.capital.repository.FundRepository;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AccountHolderRepository accountHolderRepository;
    private final CircleRepository circleRepository;
    private final LoanRepository loanRepository;
    private final FundRepository fundRepository;
    private final OperationRepository operationRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        long totalUsers = accountHolderRepository.count();
        long activeCircles = circleRepository.countByStatus(CircleStatus.ACTIVE);
        long activeLoans = loanRepository.countByStatusIn(List.of(
                LoanStatus.APPROVED,
                LoanStatus.DISBURSED,
                LoanStatus.ACTIVE
        ));

        // Legacy inventory metric kept for admin observability while the capital module is being retired.
        BigDecimal capitalAvailable = fundRepository.sumAvailableCapital();
        if (capitalAvailable == null) {
            capitalAvailable = BigDecimal.ZERO;
        }

        long totalSubscriptions = operationRepository.countByType(OperationType.SUBSCRIPTION);
        long confirmedSubscriptions = operationRepository.countByTypeAndStatus(OperationType.SUBSCRIPTION, OperationStatus.CONFIRMED);
        long pendingSubscriptions = operationRepository.countByTypeAndStatus(OperationType.SUBSCRIPTION, OperationStatus.INITIATED);
        long failedSubscriptions = operationRepository.countByTypeAndStatus(OperationType.SUBSCRIPTION, OperationStatus.FAILED)
                + operationRepository.countByTypeAndStatus(OperationType.SUBSCRIPTION, OperationStatus.CANCELED);

        SubscriptionStatsResponse subscriptionStats = new SubscriptionStatsResponse(
                totalSubscriptions,
                confirmedSubscriptions,
                pendingSubscriptions,
                failedSubscriptions
        );

        return new AdminDashboardResponse(
                totalUsers,
                activeCircles,
                activeLoans,
                capitalAvailable,
                subscriptionStats
        );
    }
}
