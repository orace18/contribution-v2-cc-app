package com.ccapp.identity.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_firebase_uid", columnList = "firebase_uid")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_firebase_uid", columnNames = "firebase_uid"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    @Column(name = "email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 16)
    private AccountStatus accountStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 32)
    private KycStatus kycStatus;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;
}
