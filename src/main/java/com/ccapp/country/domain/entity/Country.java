package com.ccapp.country.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.country.domain.enums.CountryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "countries", indexes = {
        @Index(name = "idx_countries_status_code", columnList = "status, code")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_countries_code", columnNames = "code")
})
public class Country extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 3)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CountryStatus status;

    @Column(name = "is_international_fallback", nullable = false)
    private boolean internationalFallback;
}
