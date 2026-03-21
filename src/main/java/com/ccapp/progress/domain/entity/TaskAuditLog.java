package com.ccapp.progress.domain.entity;

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
@Table(name = "task_audit_logs", indexes = {
        @Index(name = "idx_task_audit_logs_account_holder_created", columnList = "account_holder_id, created_at"),
        @Index(name = "idx_task_audit_logs_user_task", columnList = "user_task_id")
})
public class TaskAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_task_id")
    private UUID userTaskId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (detailsJson == null || detailsJson.isBlank()) {
            detailsJson = "{}";
        }
    }
}
