package com.ccapp.prayer.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.prayer.domain.enums.PrayerDeepLinkRouteKey;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
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
@Table(name = "prayer_posts", indexes = {
        @Index(name = "idx_prayer_posts_status_published", columnList = "lifecycle_status, published_at"),
        @Index(name = "idx_prayer_posts_visibility_created", columnList = "visibility, created_at"),
        @Index(name = "idx_prayer_posts_author_created", columnList = "author_account_holder_id, created_at")
})
public class PrayerPost extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 16)
    private PrayerPostType postType;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    @Builder.Default
    private PrayerPostLifecycleStatus lifecycleStatus = PrayerPostLifecycleStatus.DRAFT;

    @Column(name = "author_account_holder_id")
    private UUID authorAccountHolderId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "created_by_admin_user_id")
    private UUID createdByAdminUserId;

    @Column(name = "title", length = 160)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private PrayerVisibilityScope visibility;

    @Column(name = "audience_circle_id")
    private UUID audienceCircleId;

    @Column(name = "audience_church_ref", length = 128)
    private String audienceChurchRef;

    @Column(name = "audience_country_code", length = 3)
    private String audienceCountryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "deep_link_route_key", length = 64)
    private PrayerDeepLinkRouteKey deepLinkRouteKey;

    @Column(name = "deep_link_label", length = 160)
    private String deepLinkLabel;

    @Column(name = "deep_link_params_json", columnDefinition = "TEXT")
    @Builder.Default
    private String deepLinkParamsJson = "{}";

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    @Column(name = "amen_count", nullable = false)
    @Builder.Default
    private int amenCount = 0;

    @Column(name = "support_count", nullable = false)
    @Builder.Default
    private int supportCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @PrePersist
    protected void onPersist() {
        if (deepLinkParamsJson == null || deepLinkParamsJson.isBlank()) {
            deepLinkParamsJson = "{}";
        }
    }
}
