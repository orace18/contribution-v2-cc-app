package com.ccapp.identity.account_holder.domain.entity;

import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationSource;
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
    @Column(name = "account_type", nullable = false, length = 16)
    private AccountType accountType;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_via", nullable = false, length = 16)
    private AccountCreatedVia createdVia;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 32)
    private KycStatus kycStatus;

    @Column(name = "kyc_verified_at")
    private OffsetDateTime kycVerifiedAt;

    @Column(name = "phone_verified_at")
    private OffsetDateTime phoneVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "phone_verification_source", length = 32)
    private PhoneVerificationSource phoneVerificationSource;

    @Column(name = "phone_verification_ip", length = 64)
    private String phoneVerificationIp;

    @Column(name = "phone_verification_device", length = 128)
    private String phoneVerificationDevice;

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
        if (accountType == null) {
            accountType = AccountType.STANDARD;
        }
        if (createdVia == null) {
            createdVia = AccountCreatedVia.SELF;
        }
    }
}
