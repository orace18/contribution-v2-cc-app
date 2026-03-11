package com.ccapp.community.domain.entity;

import com.ccapp.common.model.AuditableEntity;
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
@Table(name = "community_posts", indexes = {
        @Index(name = "idx_community_posts_created_at", columnList = "created_at"),
        @Index(name = "idx_community_posts_featured_priority", columnList = "featured, featured_priority, featured_at")
})
public class CommunityPost extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_url", length = 512)
    private String mediaUrl;

    @Column(name = "author_account_holder_id")
    private UUID authorAccountHolderId;

    @Column(name = "community_id")
    private UUID communityId;

    @Column(name = "visibility", length = 32)
    private String visibility;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "featured", nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(name = "featured_priority")
    private Integer featuredPriority;

    @Column(name = "featured_at")
    private OffsetDateTime featuredAt;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
    }
}
