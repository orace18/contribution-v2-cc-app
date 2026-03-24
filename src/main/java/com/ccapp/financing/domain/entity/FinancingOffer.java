package com.ccapp.financing.domain.entity;

import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.FinancialProductType;
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
@Table(name = "financing_offers", indexes = {
        @Index(name = "idx_financing_offers_partner_status", columnList = "partner_id, status")
})
public class FinancingOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "name", nullable = false, length = 180)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 16)
    private FinancialProductType type;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "fees_json", nullable = false, columnDefinition = "TEXT")
    private String feesJson;

    @Column(name = "eligibility_rules_json", nullable = false, columnDefinition = "TEXT")
    private String eligibilityRulesJson;

    @Column(name = "required_documents_json", nullable = false, columnDefinition = "TEXT")
    private String requiredDocumentsJson;

    @Column(name = "scoring_rules_json", nullable = false, columnDefinition = "TEXT")
    private String scoringRulesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FinancingOfferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = FinancingOfferStatus.ACTIVE;
        }
        if (feesJson == null || feesJson.isBlank()) {
            feesJson = "{}";
        }
        if (eligibilityRulesJson == null || eligibilityRulesJson.isBlank()) {
            eligibilityRulesJson = "{}";
        }
        if (requiredDocumentsJson == null || requiredDocumentsJson.isBlank()) {
            requiredDocumentsJson = "{}";
        }
        if (scoringRulesJson == null || scoringRulesJson.isBlank()) {
            scoringRulesJson = "{}";
        }
    }
}
