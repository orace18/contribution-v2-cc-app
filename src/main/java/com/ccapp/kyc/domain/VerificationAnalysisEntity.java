package com.ccapp.kyc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "verification_analysis")
public class VerificationAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false, unique = true)
    private IdentityVerificationEntity verification;

    @Column(name = "document_readable")
    private Boolean documentReadable;

    @Column(name = "ocr_confidence")
    private Integer ocrConfidence;

    @Column(name = "extracted_full_name")
    private String extractedFullName;

    @Column(name = "extracted_document_number")
    private String extractedDocumentNumber;

    @Column(name = "extracted_birth_date")
    private LocalDate extractedBirthDate;

    @Column(name = "extracted_expiry_date")
    private LocalDate extractedExpiryDate;

    @Column(name = "extracted_nationality")
    private String extractedNationality;

    @Column(name = "document_expired")
    private Boolean documentExpired;

    @Column(name = "face_match_score")
    private Integer faceMatchScore;

    @Column(name = "face_matched")
    private Boolean faceMatched;

    @Column(name = "score_details_json", columnDefinition = "TEXT")
    private String scoreDetailsJson;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private OffsetDateTime analyzedAt;

    @PrePersist
    protected void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = OffsetDateTime.now();
        }
    }
}
