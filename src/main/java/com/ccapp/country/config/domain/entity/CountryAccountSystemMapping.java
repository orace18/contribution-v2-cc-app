package com.ccapp.country.config.domain.entity;

import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(CountryAccountSystemMappingId.class)
@Table(name = "country_account_system_mapping")
public class CountryAccountSystemMapping {

    @Id
    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "system_tag", nullable = false, length = 64)
    private CountryAccountSystemTag systemTag;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    protected void normalize() {
        if (countryCode != null) {
            countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        }
    }
}
