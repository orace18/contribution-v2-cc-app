package com.ccapp.commerce.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.commerce.domain.enums.CommerceDistributionMode;
import com.ccapp.commerce.domain.enums.CommerceSettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commerce_orders", indexes = {
        @Index(name = "idx_commerce_orders_buyer_created", columnList = "buyer_account_holder_id, created_at"),
        @Index(name = "idx_commerce_orders_merchant_created", columnList = "merchant_account_holder_id, created_at"),
        @Index(name = "idx_commerce_orders_status_created", columnList = "settlement_status, created_at")
})
public class CommerceOrder extends AuditableEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "buyer_account_holder_id", nullable = false)
    private UUID buyerAccountHolderId;

    @Column(name = "merchant_account_holder_id", nullable = false)
    private UUID merchantAccountHolderId;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "net_paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netPaidAmount;

    @Column(name = "total_dsb_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDsbAmount;

    @Column(name = "platform_commission", nullable = false, precision = 19, scale = 4)
    private BigDecimal platformCommission;

    @Column(name = "merchant_net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal merchantNetAmount;

    @Column(name = "volume_cv", nullable = false, precision = 19, scale = 4)
    private BigDecimal volumeCv;

    @Enumerated(EnumType.STRING)
    @Column(name = "distribution_mode", nullable = false, length = 32)
    private CommerceDistributionMode distributionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 32)
    private CommerceSettlementStatus settlementStatus;

    @Column(name = "delivery_confirmed_at")
    private OffsetDateTime deliveryConfirmedAt;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;
}
