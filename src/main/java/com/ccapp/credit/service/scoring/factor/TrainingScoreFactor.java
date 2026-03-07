package com.ccapp.credit.service.scoring.factor;

import com.ccapp.credit.service.scoring.CreditScoreFactor;
import com.ccapp.credit.service.scoring.CreditScoringContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Order(40)
public class TrainingScoreFactor implements CreditScoreFactor {

    @Override
    public String key() {
        return "training";
    }

    @Override
    public BigDecimal evaluate(CreditScoringContext context) {
        return context.circleScore()
                .map(score -> score.trainingScore().setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
