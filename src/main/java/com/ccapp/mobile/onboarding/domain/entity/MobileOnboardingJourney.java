package com.ccapp.mobile.onboarding.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mobile_onboarding_journeys", indexes = {
        @Index(name = "idx_mobile_onboarding_journeys_status", columnList = "status"),
        @Index(name = "idx_mobile_onboarding_journeys_plan_id", columnList = "plan_id"),
        @Index(name = "idx_mobile_onboarding_journeys_payment_operation_id", columnList = "payment_operation_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_mobile_onboarding_journeys_account_holder", columnNames = "account_holder_id")
})
public class MobileOnboardingJourney extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MobileOnboardingJourneyStatus status;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "sponsor_set", nullable = false)
    private boolean sponsorSet;

    @Column(name = "sponsor_account_holder_id")
    private UUID sponsorAccountHolderId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_code", length = 64)
    private String planCode;

    @Column(name = "payment_operation_id")
    private UUID paymentOperationId;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "network_linked", nullable = false)
    private boolean networkLinked;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;
}
