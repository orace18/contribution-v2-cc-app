package com.ccapp.progress.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.progress.domain.enums.TaskTriggerEvent;
import com.ccapp.progress.domain.enums.TaskType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_definitions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_definitions_task_type", columnNames = "task_type")
})
public class TaskDefinition extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private TaskType taskType;

    @Column(name = "title_template", nullable = false, length = 255)
    private String titleTemplate;

    @Column(name = "description_template", length = 2000)
    private String descriptionTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false, length = 64)
    private TaskTriggerEvent triggerEvent;

    @Column(name = "action_json", nullable = false, columnDefinition = "TEXT")
    private String actionJson;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (metadataJson == null || metadataJson.isBlank()) {
            metadataJson = "{}";
        }
    }
}
