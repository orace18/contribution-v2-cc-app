package com.ccapp.wallet.domain.entity;

import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.wallet.domain.enums.WalletOtpChallengeStatus;
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
@Table(name = "wallet_otp_challenges", indexes = {
        @Index(name = "idx_wallet_otp_challenges_wallet_status", columnList = "wallet_id,status"),
        @Index(name = "idx_wallet_otp_challenges_wallet_type_fingerprint_status",
                columnList = "wallet_id,operation_type,fingerprint_hash,status")
})
public class WalletOtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "operation_id")
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType;

    @Column(name = "fingerprint_hash", nullable = false, length = 128)
    private String fingerprintHash;

    @Column(name = "otp_hash", nullable = false, length = 128)
    private String otpHash;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WalletOtpChallengeStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (status == null) {
            status = WalletOtpChallengeStatus.PENDING;
        }
    }
}
