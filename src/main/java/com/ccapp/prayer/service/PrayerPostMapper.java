package com.ccapp.prayer.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.entity.PrayerPostMediaRef;
import com.ccapp.prayer.domain.enums.PrayerAuthorType;
import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.dto.AdminPrayerPostResponse;
import com.ccapp.prayer.dto.InternalDeepLinkResponse;
import com.ccapp.prayer.dto.PrayerAuthorResponse;
import com.ccapp.prayer.dto.PrayerMediaRefResponse;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.dto.PrayerScopeContextResponse;
import com.ccapp.prayer.dto.PrayerStatsResponse;
import com.ccapp.prayer.dto.PrayerViewerStateResponse;
import com.ccapp.prayer.repository.PrayerAmenRepository;
import com.ccapp.prayer.repository.PrayerPostMediaRefRepository;
import com.ccapp.prayer.repository.PrayerSupportRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PrayerPostMapper {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final AccountHolderRepository accountHolderRepository;
    private final MediaFileRepository mediaFileRepository;
    private final PrayerPostMediaRefRepository prayerPostMediaRefRepository;
    private final PrayerAmenRepository prayerAmenRepository;
    private final PrayerSupportRepository prayerSupportRepository;
    private final ObjectMapper objectMapper;

    PrayerPostResponse toMobileResponse(PrayerPost post, UUID viewerAccountHolderId) {
        return new PrayerPostResponse(
                post.getId(),
                post.getPostType(),
                post.getLifecycleStatus(),
                post.getTitle(),
                post.getContent(),
                post.getVisibility(),
                buildAuthor(post),
                new PrayerScopeContextResponse(
                        post.getAudienceCircleId(),
                        post.getAudienceChurchRef(),
                        post.getAudienceCountryCode()
                ),
                loadMediaRefs(post.getId()),
                buildDeepLink(post),
                new PrayerStatsResponse(
                        post.getAmenCount(),
                        post.getSupportCount(),
                        post.getCommentCount()
                ),
                buildViewerState(post, viewerAccountHolderId),
                post.getCreatedAt(),
                post.getPublishedAt()
        );
    }

    AdminPrayerPostResponse toAdminResponse(PrayerPost post) {
        return new AdminPrayerPostResponse(
                post.getId(),
                post.getPostType(),
                post.getLifecycleStatus(),
                post.getTitle(),
                post.getContent(),
                mapOfficialVisibility(post),
                post.getAudienceCountryCode(),
                post.getAudienceChurchRef(),
                loadMediaRefs(post.getId()),
                buildDeepLink(post),
                post.getCreatedByAdminUserId(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getPublishedAt()
        );
    }

    private PrayerAuthorResponse buildAuthor(PrayerPost post) {
        if (post.getPostType() == PrayerPostType.OFFICIAL) {
            return new PrayerAuthorResponse(null, "Club Chretien", PrayerAuthorType.ADMIN_PLATFORM, null);
        }

        AccountHolder author = post.getAuthorAccountHolderId() == null
                ? null
                : accountHolderRepository.findById(post.getAuthorAccountHolderId()).orElse(null);
        String displayName = author == null
                ? "Member"
                : ((author.getFirstName() == null ? "" : author.getFirstName().trim()) + " " +
                   (author.getLastName() == null ? "" : author.getLastName().trim())).trim();
        if (displayName.isBlank()) {
            displayName = "Member";
        }
        return new PrayerAuthorResponse(post.getAuthorAccountHolderId(), displayName, PrayerAuthorType.ACCOUNT_HOLDER, null);
    }

    private List<PrayerMediaRefResponse> loadMediaRefs(UUID postId) {
        return prayerPostMediaRefRepository.findByPostIdOrderBySortOrderAsc(postId).stream()
                .map(this::toMediaRefResponse)
                .toList();
    }

    private PrayerMediaRefResponse toMediaRefResponse(PrayerPostMediaRef ref) {
        MediaFile mediaFile = mediaFileRepository.findById(ref.getMediaFileId()).orElse(null);
        return new PrayerMediaRefResponse(
                ref.getMediaFileId(),
                ref.getUsageType(),
                ref.getSortOrder(),
                mediaFile == null ? null : mediaFile.getContentType(),
                null
        );
    }

    private InternalDeepLinkResponse buildDeepLink(PrayerPost post) {
        if (post.getDeepLinkRouteKey() == null) {
            return null;
        }
        try {
            Map<String, String> params = post.getDeepLinkParamsJson() == null || post.getDeepLinkParamsJson().isBlank()
                    ? Collections.emptyMap()
                    : objectMapper.readValue(post.getDeepLinkParamsJson(), STRING_MAP);
            return new InternalDeepLinkResponse(post.getDeepLinkRouteKey(), post.getDeepLinkLabel(), params);
        } catch (Exception ex) {
            return new InternalDeepLinkResponse(post.getDeepLinkRouteKey(), post.getDeepLinkLabel(), Collections.emptyMap());
        }
    }

    private PrayerViewerStateResponse buildViewerState(PrayerPost post, UUID viewerAccountHolderId) {
        boolean hasAmened = viewerAccountHolderId != null
                && prayerAmenRepository.existsByPostIdAndAccountHolderId(post.getId(), viewerAccountHolderId);
        boolean hasSupported = viewerAccountHolderId != null
                && prayerSupportRepository.existsByPostIdAndAccountHolderId(post.getId(), viewerAccountHolderId);
        boolean canSupport = post.getPostType() == PrayerPostType.REQUEST;
        return new PrayerViewerStateResponse(
                hasAmened,
                hasSupported,
                true,
                canSupport,
                true
        );
    }

    private PrayerOfficialVisibilityScope mapOfficialVisibility(PrayerPost post) {
        return switch (post.getVisibility()) {
            case CHURCH -> PrayerOfficialVisibilityScope.CHURCH;
            case COMMUNITY -> PrayerOfficialVisibilityScope.COMMUNITY;
            case GLOBAL -> PrayerOfficialVisibilityScope.GLOBAL;
            default -> PrayerOfficialVisibilityScope.GLOBAL;
        };
    }
}
