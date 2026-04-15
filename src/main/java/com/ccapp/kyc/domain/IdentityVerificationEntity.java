package com.ccapp.kyc.domain;

import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "identity_verifications",
        indexes = {
                @Index(name = "idx_identity_verif_account_holder", columnList = "account_holder_id"),
                @Index(name = "idx_identity_verif_status", columnList = "status")
        })
public class IdentityVerificationEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "reference_id")
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VerificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 32)
    private VerificationStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private VerificationDocumentType documentType;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "reliability_score")
    private Integer reliabilityScore;

    @Column(name = "decision_reason", length = 64)
    private String decisionReason;
}
