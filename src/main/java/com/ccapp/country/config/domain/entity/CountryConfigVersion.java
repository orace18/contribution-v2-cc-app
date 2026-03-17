package com.ccapp.country.config.domain.entity;

import com.ccapp.country.config.domain.enums.CountryConfigType;
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
import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "country_config_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_country_config_versions_country_type_version",
                columnNames = {"country_code", "config_type", "version"}
        ),
        indexes = {
                @Index(name = "idx_country_config_versions_country_type_effective",
                        columnList = "country_code, config_type, effective_date"),
                @Index(name = "idx_country_config_versions_country_effective",
                        columnList = "country_code, effective_date")
        })
public class CountryConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_type", nullable = false, length = 64)
    private CountryConfigType configType;

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
        if (countryCode != null) {
            countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        }
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
