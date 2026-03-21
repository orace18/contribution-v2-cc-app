package com.ccapp.progress.domain.entity;

import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
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
@Table(name = "user_tasks", indexes = {
        @Index(name = "idx_user_tasks_account_holder_id", columnList = "account_holder_id"),
        @Index(name = "idx_user_tasks_account_holder_status", columnList = "account_holder_id, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_tasks_active_key", columnNames = {"account_holder_id", "active_key"})
})
public class UserTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private TaskType taskType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaskStatus status;

    @Column(name = "priority_score", nullable = false)
    private int priorityScore;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "action_json", columnDefinition = "TEXT")
    private String actionJson;

    @Column(name = "active_key", length = 64)
    private String activeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        refreshActiveKey();
    }

    @PreUpdate
    protected void onUpdate() {
        refreshActiveKey();
    }

    private void refreshActiveKey() {
        activeKey = status != null && status.active() && taskType != null ? taskType.name() : null;
    }
}
