package com.ccapp.identity.delegation.domain.entity;

import com.ccapp.identity.delegation.domain.enums.AssistedMemberVerificationType;
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
@Table(name = "assisted_member_verifications", indexes = {
        @Index(name = "idx_assisted_member_verifications_account_holder_created_at", columnList = "account_holder_id, created_at")
})
public class AssistedMemberVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "verified_by", nullable = false)
    private UUID verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 16)
    private AssistedMemberVerificationType verificationType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
