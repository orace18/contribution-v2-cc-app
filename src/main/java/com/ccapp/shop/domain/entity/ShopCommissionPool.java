package com.ccapp.shop.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "shop_commission_pool",
        uniqueConstraints = @UniqueConstraint(name = "uk_shop_commission_pool_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_shop_commission_pool_order", columnList = "order_id"))
public class ShopCommissionPool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shop_commission_pool_order"))
    private ShopOrder order;

    @Column(name = "merchant_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal merchantAmount;

    @Column(name = "platform_commission", nullable = false, precision = 19, scale = 4)
    private BigDecimal platformCommission;

    @Column(name = "mlm_volume", nullable = false, precision = 19, scale = 4)
    private BigDecimal mlmVolume;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
