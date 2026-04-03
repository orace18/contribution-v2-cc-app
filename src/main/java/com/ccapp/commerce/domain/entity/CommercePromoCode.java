package com.ccapp.commerce.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.commerce.domain.enums.CommercePromoCodeStatus;
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
@Table(name = "commerce_promo_codes", indexes = {
        @Index(name = "idx_commerce_promo_codes_owner_created", columnList = "owner_account_holder_id, created_at"),
        @Index(name = "idx_commerce_promo_codes_country_status", columnList = "country, status")
})
public class CommercePromoCode extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "owner_account_holder_id", nullable = false)
    private UUID ownerAccountHolderId;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CommercePromoCodeStatus status;

    @Column(name = "expiry_date", nullable = false)
    private OffsetDateTime expiryDate;

    @Column(name = "usage_limit", nullable = false)
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;
}
