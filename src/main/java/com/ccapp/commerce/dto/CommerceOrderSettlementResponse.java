package com.ccapp.commerce.dto;

import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import com.ccapp.shop.domain.enums.ShopOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CommerceOrderSettlementResponse(
        UUID orderId,
        ShopOrderStatus orderStatus,
        CommerceSettlementStatus settlementStatus,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal netPaidAmount,
        BigDecimal platformCommission,
        BigDecimal totalDsbAmount,
        BigDecimal merchantNetAmount,
        BigDecimal volumeCv,
        CommerceDistributionMode distributionMode,
        OffsetDateTime deliveredAt,
        OffsetDateTime settledAt,
        List<CommerceOrderSettlementBeneficiaryResponse> beneficiaries
) {
}
