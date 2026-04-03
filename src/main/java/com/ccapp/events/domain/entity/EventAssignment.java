package com.ccapp.events.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.events.domain.enums.EventAssignmentRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_assignments_event_account_role", columnNames = {"event_id", "account_holder_id", "role"})
}, indexes = {
        @Index(name = "idx_event_assignments_account_role", columnList = "account_holder_id, role")
})
public class EventAssignment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private EventAssignmentRole role;
}
