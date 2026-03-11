package com.ccapp.mlm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(MlmRelationshipId.class)
@Table(name = "mlm_relationships", indexes = {
        @Index(name = "idx_mlm_relationships_ancestor_level_created", columnList = "ancestor_id, level, created_at"),
        @Index(name = "idx_mlm_relationships_descendant_level", columnList = "descendant_id, level")
})
public class MlmRelationship {

    @Id
    @Column(name = "ancestor_id", nullable = false)
    private UUID ancestorId;

    @Id
    @Column(name = "descendant_id", nullable = false)
    private UUID descendantId;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
