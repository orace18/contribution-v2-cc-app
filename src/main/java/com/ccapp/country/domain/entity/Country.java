package com.ccapp.country.domain.entity;

import com.ccapp.common.model.AuditableEntity;
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
        @Index(name = "idx_countries_active_name", columnList = "active, name")
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

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "configured", nullable = false)
    private boolean configured;

    @Column(name = "fallback", nullable = false)
    private boolean fallback;
}
