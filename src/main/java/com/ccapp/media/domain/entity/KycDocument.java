package com.ccapp.media.domain.entity;

import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
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
@Table(name = "kyc_documents", indexes = {
        @Index(name = "idx_kyc_documents_account_holder_type", columnList = "account_holder_id, document_type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_kyc_documents_media_file", columnNames = "media_file_id")
})
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "media_file_id", nullable = false)
    private UUID mediaFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 64)
    private KycDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KycDocumentStatus status;

    @Column(name = "cpass_case_id", length = 128)
    private String cpassCaseId;

    @Column(name = "cpass_submitted_at")
    private OffsetDateTime cpassSubmittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = KycDocumentStatus.SUBMITTED;
        }
    }
}
