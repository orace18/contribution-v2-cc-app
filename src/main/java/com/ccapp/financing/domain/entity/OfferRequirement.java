package com.ccapp.financing.domain.entity;

import com.ccapp.financing.domain.enums.OfferRequirementType;
import com.ccapp.financing.domain.enums.OfferVerificationType;
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
@Table(name = "offer_requirements",
        indexes = {
                @Index(name = "idx_offer_requirements_offer_required", columnList = "offer_id, required")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_offer_requirements_offer_type", columnNames = {"offer_id", "requirement_type"})
        })
public class OfferRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 32)
    private OfferRequirementType requirementType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 32)
    private OfferVerificationType verificationType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
