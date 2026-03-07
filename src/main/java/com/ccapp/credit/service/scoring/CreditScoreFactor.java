package com.ccapp.credit.service.scoring;

import java.math.BigDecimal;

public interface CreditScoreFactor {

    String key();

    BigDecimal evaluate(CreditScoringContext context);
}
