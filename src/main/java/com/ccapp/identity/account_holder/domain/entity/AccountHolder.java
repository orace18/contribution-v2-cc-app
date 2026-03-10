package com.ccapp.identity.account_holder.domain.entity;

import com.ccapp.identity.domain.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_holders")
public class AccountHolder {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "service_country_code", length = 3)
    private String serviceCountryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 16)
    private KycStatus kycStatus;

    @Column(name = "kyc_verified_at")
    private OffsetDateTime kycVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (kycStatus == null) {
            kycStatus = KycStatus.NOT_STARTED;
        }
    }
}
