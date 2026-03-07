package com.ccapp.credit.service.scoring.factor;

import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.service.scoring.CreditScoreFactor;
import com.ccapp.credit.service.scoring.CreditScoringContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@Order(10)
@RequiredArgsConstructor
public class RmsScoreFactor implements CreditScoreFactor {

    private final LoanRepository loanRepository;

    @Override
    public String key() {
        return "rms";
    }

    @Override
    public BigDecimal evaluate(CreditScoringContext context) {
        long closedLoans = loanRepository.countByAccountHolderIdAndStatusIn(
                context.accountHolderId(),
                List.of(LoanStatus.REPAID, LoanStatus.DEFAULTED)
        );

        if (closedLoans == 0) {
            return new BigDecimal("50.00");
        }

        long repaidLoans = loanRepository.countByAccountHolderIdAndStatus(context.accountHolderId(), LoanStatus.REPAID);
        return BigDecimal.valueOf(repaidLoans * 100L)
                .divide(BigDecimal.valueOf(closedLoans), 2, RoundingMode.HALF_UP);
    }
}
