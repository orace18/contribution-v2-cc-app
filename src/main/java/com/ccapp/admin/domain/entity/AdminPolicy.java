package com.ccapp.admin.domain.entity;

import com.ccapp.admin.domain.enums.AdminPolicyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_policies", indexes = {
        @Index(name = "idx_admin_policies_type_country", columnList = "policy_type, country_code")
})
public class AdminPolicy {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 64)
    private AdminPolicyType policyType;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (countryCode != null) {
            countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
