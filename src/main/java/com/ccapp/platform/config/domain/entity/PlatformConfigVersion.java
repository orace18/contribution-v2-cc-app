package com.ccapp.platform.config.domain.entity;

import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_config_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_platform_config_versions_type_version",
                columnNames = {"config_type", "version"}
        ),
        indexes = {
                @Index(name = "idx_platform_config_versions_type_effective",
                        columnList = "config_type, effective_date")
        })
public class PlatformConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_type", nullable = false, length = 64)
    private PlatformConfigType configType;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "effective_date", nullable = false)
    private OffsetDateTime effectiveDate;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_by_admin_user_id")
    private UUID createdByAdminUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (effectiveDate == null) {
            effectiveDate = OffsetDateTime.now();
        }
        if (version == null || version <= 0) {
            version = 1;
        }
    }
}
