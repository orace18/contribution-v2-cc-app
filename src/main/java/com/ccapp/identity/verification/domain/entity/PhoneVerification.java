package com.ccapp.identity.verification.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationChannel;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
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
@Table(name = "phone_verifications", indexes = {
        @Index(name = "idx_phone_verifications_requester_created", columnList = "requester_account_holder_id, created_at"),
        @Index(name = "idx_phone_verifications_phone_status", columnList = "phone_number, status"),
        @Index(name = "idx_phone_verifications_expires_at", columnList = "expires_at")
})
public class PhoneVerification extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requester_account_holder_id", nullable = false)
    private UUID requesterAccountHolderId;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private PhoneVerificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PhoneVerificationStatus status;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "max_send_count", nullable = false)
    private int maxSendCount;

    @Column(name = "last_sent_at", nullable = false)
    private OffsetDateTime lastSentAt;

    @Column(name = "resend_available_at", nullable = false)
    private OffsetDateTime resendAvailableAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @PrePersist
    protected void onPersistDefaults() {
        if (status == null) {
            status = PhoneVerificationStatus.PENDING;
        }
    }
}
