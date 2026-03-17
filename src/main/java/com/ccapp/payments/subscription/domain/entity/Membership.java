package com.ccapp.payments.subscription.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "memberships", indexes = {
        @Index(name = "idx_memberships_account_holder_status", columnList = "account_holder_id, status"),
        @Index(name = "idx_memberships_country_status", columnList = "country_code, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_memberships_operation_id", columnNames = "operation_id")
})
public class Membership extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "plan_code", nullable = false, length = 64)
    private String planCode;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MembershipStatus status;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "config_snapshot", columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(name = "config_version")
    private Integer configVersion;
}
