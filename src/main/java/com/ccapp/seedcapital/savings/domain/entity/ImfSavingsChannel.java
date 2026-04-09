package com.ccapp.seedcapital.savings.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsChannelStatus;
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
@Table(name = "imf_savings_channels", indexes = {
        @Index(name = "idx_imf_savings_channels_account_country", columnList = "account_holder_id, country_code, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_imf_savings_channels_account_country", columnNames = {"account_holder_id", "country_code"})
})
public class ImfSavingsChannel extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 16)
    private FinancialPartnerType partnerType;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImfSavingsChannelStatus status;

    @Column(name = "partner_member_reference", length = 128)
    private String partnerMemberReference;

    @Column(name = "external_channel_reference", length = 128)
    private String externalChannelReference;

    @Column(name = "readiness_note", length = 500)
    private String readinessNote;

    @Column(name = "configured_at", nullable = false)
    private OffsetDateTime configuredAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @PrePersist
    protected void onPersist() {
        if (partnerType == null) {
            partnerType = FinancialPartnerType.IMF;
        }
        if (status == null) {
            status = ImfSavingsChannelStatus.PENDING_PARTNER_CONFIRMATION;
        }
        if (configuredAt == null) {
            configuredAt = OffsetDateTime.now();
        }
    }
}
