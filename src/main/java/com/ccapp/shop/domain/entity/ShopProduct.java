package com.ccapp.shop.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "shop_products", indexes = {
        @Index(name = "idx_shop_products_merchant_active", columnList = "merchant_id, active"),
        @Index(name = "idx_shop_products_active_name", columnList = "active, name")
})
public class ShopProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(name = "cv_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal cvValue;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "category", length = 128)
    private String category;

    @Column(name = "image_url", length = 512)
    private String imageUrl;
}
