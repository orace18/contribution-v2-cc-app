package com.ccapp.kyc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "verification_documents")
public class VerificationDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false)
    private IdentityVerificationEntity verification;

    @Column(name = "document_front_key", nullable = false)
    private String documentFrontKey;

    @Column(name = "document_back_key")
    private String documentBackKey;

    @Column(name = "selfie_key", nullable = false)
    private String selfieKey;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "capture_device")
    private String captureDevice;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "platform")
    private String platform;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = OffsetDateTime.now();
        }
    }
}
