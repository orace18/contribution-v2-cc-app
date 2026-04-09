package com.ccapp.capital.domain.entity;

import com.ccapp.capital.domain.enums.FundProviderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "funds", indexes = {
        @Index(name = "idx_funds_provider", columnList = "provider")
})
@Deprecated(since = "2026-04", forRemoval = false)
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private FundProviderType provider;

    @Column(name = "total_capital", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCapital;

    @Column(name = "available_capital", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableCapital;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (availableCapital == null && totalCapital != null) {
            availableCapital = totalCapital;
        }
    }
}
