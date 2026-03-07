package com.ccapp.media.domain.entity;

import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;
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
@Table(name = "media_files", indexes = {
        @Index(name = "idx_media_files_account_holder_created_at", columnList = "account_holder_id, created_at"),
        @Index(name = "idx_media_files_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_media_files_bucket_object_key", columnNames = {"bucket", "object_key"})
})
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 64)
    private MediaType type;

    @Column(name = "bucket", nullable = false, length = 64)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size", nullable = false)
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MediaStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = MediaStatus.PENDING_UPLOAD;
        }
    }
}
