package com.ccapp.payments.subscription.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscription_plans", indexes = {
        @Index(name = "idx_subscription_plans_country_currency", columnList = "country, currency")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_plans_code_country_currency", columnNames = {"code", "country", "currency"})
})
public class SubscriptionPlan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active;
}
