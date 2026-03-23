package com.ccapp.payments.subscription.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
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
        @Index(name = "idx_subscription_plans_country_currency", columnList = "country, currency"),
        @Index(name = "idx_subscription_plans_country_grade_duration", columnList = "country, grade, duration_months")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_plans_code_country_currency", columnNames = {"code", "country", "currency"}),
        @UniqueConstraint(name = "uk_subscription_plans_country_grade_duration_currency",
                columnNames = {"country", "grade", "duration_months", "currency"})
})
public class SubscriptionPlan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 16)
    private SubscriptionGrade grade;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active;
}
