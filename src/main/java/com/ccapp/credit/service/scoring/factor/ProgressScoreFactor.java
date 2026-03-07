package com.ccapp.credit.service.scoring.factor;

import com.ccapp.credit.service.scoring.CreditScoreFactor;
import com.ccapp.credit.service.scoring.CreditScoringContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Order(30)
public class ProgressScoreFactor implements CreditScoreFactor {

    @Override
    public String key() {
        return "progress";
    }

    @Override
    public BigDecimal evaluate(CreditScoringContext context) {
        return context.userProgress()
                .map(progress -> progress.progressPercentage().setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
