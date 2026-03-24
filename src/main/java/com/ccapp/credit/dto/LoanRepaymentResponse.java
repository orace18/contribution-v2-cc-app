package com.ccapp.credit.dto;

import com.ccapp.credit.domain.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanRepaymentResponse(
        UUID repaymentId,
        UUID loanId,
        Integer installmentNumber,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        UUID institutionId,
        String transactionId,
        OffsetDateTime paidAt,
        LoanStatus loanStatus,
        long remainingInstallments,
        UUID operationId,
        boolean otpRequired,
        UUID otpChallengeId,
        OffsetDateTime otpExpiresAt
) {
}
