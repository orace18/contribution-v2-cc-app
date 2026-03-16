package com.ccapp.financing.service;

import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.entity.LoanPortfolioSnapshot;
import com.ccapp.financing.domain.entity.PartnerReport;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.repository.LoanApplicationRepository;
import com.ccapp.financing.repository.LoanPortfolioSnapshotRepository;
import com.ccapp.financing.repository.PartnerReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerReportingService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final PartnerReportRepository partnerReportRepository;
    private final LoanPortfolioSnapshotRepository loanPortfolioSnapshotRepository;

    @Transactional
    public void refreshDailySnapshot(UUID partnerId) {
        List<LoanApplication> applications = loanApplicationRepository.findByPartnerId(partnerId);

        BigDecimal disbursedVolume = applications.stream()
                .filter(application -> application.getStatus() == LoanApplicationStatus.DISBURSED
                        || application.getStatus() == LoanApplicationStatus.REPAID)
                .map(this::effectiveAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal repaidVolume = applications.stream()
                .filter(application -> application.getStatus() == LoanApplicationStatus.REPAID)
                .map(this::effectiveAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long disbursedCount = applications.stream()
                .filter(application -> application.getStatus() == LoanApplicationStatus.DISBURSED
                        || application.getStatus() == LoanApplicationStatus.REPAID)
                .count();
        long repaidCount = applications.stream()
                .filter(application -> application.getStatus() == LoanApplicationStatus.REPAID)
                .count();
        long defaults = applications.stream()
                .filter(application -> application.getStatus() == LoanApplicationStatus.REJECTED)
                .count();

        BigDecimal repaymentRate = disbursedCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(repaidCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(disbursedCount), 2, RoundingMode.HALF_UP);
        BigDecimal portfolioValue = disbursedVolume.subtract(repaidVolume);
        if (portfolioValue.signum() < 0) {
            portfolioValue = BigDecimal.ZERO;
        }
        BigDecimal reportPortfolioValue = portfolioValue;

        LocalDate today = LocalDate.now();
        LoanPortfolioSnapshot snapshot = loanPortfolioSnapshotRepository
                .findByPartnerIdAndSnapshotDate(partnerId, today)
                .orElseGet(() -> LoanPortfolioSnapshot.builder()
                        .partnerId(partnerId)
                        .snapshotDate(today)
                        .build());
        snapshot.setLoanVolume(disbursedVolume);
        snapshot.setRepaymentRate(repaymentRate);
        snapshot.setDefaults(defaults);
        snapshot.setPortfolioValue(portfolioValue);
        loanPortfolioSnapshotRepository.save(snapshot);

        partnerReportRepository.findFirstByPartnerIdAndReportTypeAndPeriodStartAndPeriodEnd(
                partnerId,
                "DAILY_PORTFOLIO",
                today,
                today
        ).orElseGet(() -> partnerReportRepository.save(PartnerReport.builder()
                .partnerId(partnerId)
                .reportType("DAILY_PORTFOLIO")
                .periodStart(today)
                .periodEnd(today)
                .loanVolume(disbursedVolume)
                .repaymentRate(repaymentRate)
                .defaults(defaults)
                .portfolioValue(reportPortfolioValue)
                .build()));
    }

    private BigDecimal effectiveAmount(LoanApplication application) {
        return application.getApprovedAmount() != null
                ? application.getApprovedAmount()
                : application.getRequestedAmount();
    }
}
