package com.ccapp.prayer.service;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.entity.PrayerPostMediaRef;
import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.ccapp.prayer.dto.AdminCreatePrayerPostRequest;
import com.ccapp.prayer.dto.AdminPrayerPostResponse;
import com.ccapp.prayer.dto.AdminUpdatePrayerPostRequest;
import com.ccapp.prayer.dto.InternalDeepLinkRequest;
import com.ccapp.prayer.dto.PrayerMediaAttachmentInput;
import com.ccapp.prayer.repository.PrayerPostMediaRefRepository;
import com.ccapp.prayer.repository.PrayerPostRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerAdminPostService {

    private static final Set<AdminRole> WRITE_ROLES = Set.of(
            AdminRole.ROLE_SUPER_ADMIN,
            AdminRole.ROLE_COUNTRY_ADMIN,
            AdminRole.ROLE_OPERATIONS
    );

    private final PrayerPostRepository prayerPostRepository;
    private final PrayerPostMediaRefRepository prayerPostMediaRefRepository;
    private final PrayerPostMapper prayerPostMapper;
    private final MediaFileRepository mediaFileRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<AdminPrayerPostResponse> listPosts(int page,
                                                           int size,
                                                           PrayerPostLifecycleStatus status,
                                                           PrayerOfficialVisibilityScope visibility) {
        List<PrayerPost> filtered = prayerPostRepository.findAll(Sort.by(
                        Sort.Order.desc("publishedAt"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )).stream()
                .filter(post -> post.getPostType() == PrayerPostType.OFFICIAL)
                .filter(post -> status == null || post.getLifecycleStatus() == status)
                .filter(post -> visibility == null || post.getVisibility() == toVisibilityScope(visibility))
                .toList();

        PageRequest pageRequest = PageRequest.of(page, size);
        int fromIndex = Math.min((int) pageRequest.getOffset(), filtered.size());
        int toIndex = Math.min(fromIndex + pageRequest.getPageSize(), filtered.size());
        List<AdminPrayerPostResponse> items = filtered.subList(fromIndex, toIndex).stream()
                .map(prayerPostMapper::toAdminResponse)
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageRequest, filtered.size()));
    }

    @Transactional
    public AdminPrayerPostResponse createPost(UUID adminUserId, AdminCreatePrayerPostRequest request) {
        assertOfficialPostWriteAllowed();
        OfficialVisibilityContext visibilityContext = resolveOfficialVisibility(
                request.visibility(),
                request.targetCountryCode(),
                request.targetChurchRef()
        );
        validateMediaRefs(request.mediaRefs());

        PrayerPost saved = prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.OFFICIAL)
                .lifecycleStatus(PrayerPostLifecycleStatus.DRAFT)
                .createdByAdminUserId(adminUserId)
                .title(request.title().trim())
                .content(request.content().trim())
                .visibility(visibilityContext.visibility())
                .audienceCountryCode(visibilityContext.targetCountryCode())
                .audienceChurchRef(visibilityContext.targetChurchRef())
                .deepLinkRouteKey(request.deepLink() == null ? null : request.deepLink().routeKey())
                .deepLinkLabel(request.deepLink() == null ? null : request.deepLink().label())
                .deepLinkParamsJson(serializeDeepLinkParams(request.deepLink()))
                .build());

        replaceMediaRefs(saved.getId(), request.mediaRefs());
        log.info("Prayer admin post created postId={} adminUserId={} visibility={}",
                saved.getId(), adminUserId, saved.getVisibility());
        return prayerPostMapper.toAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public AdminPrayerPostResponse getPost(UUID postId) {
        return prayerPostMapper.toAdminResponse(findOfficialPost(postId));
    }

    @Transactional
    public AdminPrayerPostResponse updatePost(UUID adminUserId, UUID postId, AdminUpdatePrayerPostRequest request) {
        assertOfficialPostWriteAllowed();
        PrayerPost post = findOfficialPost(postId);
        if (post.getLifecycleStatus() != PrayerPostLifecycleStatus.DRAFT) {
            throw new ConflictException("PRAYER_POST_UPDATE_NOT_ALLOWED");
        }

        PrayerOfficialVisibilityScope requestedVisibility = request.visibility() == null
                ? toOfficialVisibility(post.getVisibility())
                : request.visibility();
        String targetCountryCode = request.targetCountryCode() == null ? post.getAudienceCountryCode() : request.targetCountryCode();
        String targetChurchRef = request.targetChurchRef() == null ? post.getAudienceChurchRef() : request.targetChurchRef();
        OfficialVisibilityContext visibilityContext = resolveOfficialVisibility(requestedVisibility, targetCountryCode, targetChurchRef);

        if (request.title() != null) {
            post.setTitle(request.title().trim());
        }
        if (request.content() != null) {
            post.setContent(request.content().trim());
        }
        post.setVisibility(visibilityContext.visibility());
        post.setAudienceCountryCode(visibilityContext.targetCountryCode());
        post.setAudienceChurchRef(visibilityContext.targetChurchRef());
        if (request.deepLink() != null) {
            post.setDeepLinkRouteKey(request.deepLink().routeKey());
            post.setDeepLinkLabel(request.deepLink().label());
            post.setDeepLinkParamsJson(serializeDeepLinkParams(request.deepLink()));
        }

        validateMediaRefs(request.mediaRefs());
        PrayerPost saved = prayerPostRepository.save(post);
        if (request.mediaRefs() != null) {
            replaceMediaRefs(saved.getId(), request.mediaRefs());
        }

        log.info("Prayer admin post updated postId={} adminUserId={} visibility={} status={}",
                postId, adminUserId, saved.getVisibility(), saved.getLifecycleStatus());
        return prayerPostMapper.toAdminResponse(saved);
    }

    @Transactional
    public AdminPrayerPostResponse publishPost(UUID adminUserId, UUID postId) {
        assertOfficialPostWriteAllowed();
        PrayerPost post = findOfficialPost(postId);
        if (post.getLifecycleStatus() != PrayerPostLifecycleStatus.DRAFT) {
            throw new ConflictException("PRAYER_POST_PUBLISH_NOT_ALLOWED");
        }

        resolveOfficialVisibility(
                toOfficialVisibility(post.getVisibility()),
                post.getAudienceCountryCode(),
                post.getAudienceChurchRef()
        );

        post.setLifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED);
        post.setPublishedAt(OffsetDateTime.now());
        post.setArchivedAt(null);
        PrayerPost saved = prayerPostRepository.save(post);

        log.info("Prayer admin post published postId={} adminUserId={}", postId, adminUserId);
        return prayerPostMapper.toAdminResponse(saved);
    }

    @Transactional
    public AdminPrayerPostResponse archivePost(UUID adminUserId, UUID postId) {
        assertOfficialPostWriteAllowed();
        PrayerPost post = findOfficialPost(postId);
        if (post.getLifecycleStatus() == PrayerPostLifecycleStatus.ARCHIVED
                || post.getLifecycleStatus() == PrayerPostLifecycleStatus.DELETED
                || post.getLifecycleStatus() == PrayerPostLifecycleStatus.MODERATED) {
            throw new ConflictException("PRAYER_POST_ARCHIVE_NOT_ALLOWED");
        }

        post.setLifecycleStatus(PrayerPostLifecycleStatus.ARCHIVED);
        post.setArchivedAt(OffsetDateTime.now());
        PrayerPost saved = prayerPostRepository.save(post);

        log.info("Prayer admin post archived postId={} adminUserId={}", postId, adminUserId);
        return prayerPostMapper.toAdminResponse(saved);
    }

    private PrayerPost findOfficialPost(UUID postId) {
        return prayerPostRepository.findById(postId)
                .filter(post -> post.getPostType() == PrayerPostType.OFFICIAL)
                .orElseThrow(() -> new NotFoundException("PRAYER_POST_NOT_FOUND"));
    }

    private void assertOfficialPostWriteAllowed() {
        if (!WRITE_ROLES.contains(AdminSecurityUtils.currentRole())) {
            throw new AccessDeniedException("PRAYER_OFFICIAL_POST_WRITE_FORBIDDEN");
        }
    }

    private OfficialVisibilityContext resolveOfficialVisibility(PrayerOfficialVisibilityScope visibility,
                                                                String targetCountryCode,
                                                                String targetChurchRef) {
        if (visibility == null) {
            throw new BusinessException("PRAYER_OFFICIAL_POST_VISIBILITY_REQUIRED");
        }
        return switch (visibility) {
            case GLOBAL -> new OfficialVisibilityContext(PrayerVisibilityScope.GLOBAL, null, null);
            case COMMUNITY -> {
                if (targetCountryCode == null || targetCountryCode.isBlank()) {
                    throw new BusinessException("PRAYER_OFFICIAL_POST_TARGET_COUNTRY_REQUIRED");
                }
                String normalized = targetCountryCode.trim().toUpperCase();
                AdminSecurityUtils.assertCountryAccess(normalized);
                yield new OfficialVisibilityContext(PrayerVisibilityScope.COMMUNITY, normalized, null);
            }
            case CHURCH -> {
                if (targetChurchRef == null || targetChurchRef.isBlank()) {
                    throw new BusinessException("PRAYER_OFFICIAL_POST_TARGET_CHURCH_REQUIRED");
                }
                yield new OfficialVisibilityContext(PrayerVisibilityScope.CHURCH, null, targetChurchRef.trim());
            }
        };
    }

    private String serializeDeepLinkParams(InternalDeepLinkRequest deepLink) {
        if (deepLink == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(deepLink.params());
        } catch (JsonProcessingException ex) {
            throw new BusinessException("PRAYER_DEEP_LINK_INVALID");
        }
    }

    private void validateMediaRefs(List<PrayerMediaAttachmentInput> mediaRefs) {
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return;
        }
        for (PrayerMediaAttachmentInput ref : mediaRefs) {
            mediaFileRepository.findById(ref.mediaFileId())
                    .orElseThrow(() -> new BusinessException("PRAYER_MEDIA_FILE_NOT_FOUND"));
        }
    }

    private void replaceMediaRefs(UUID postId, List<PrayerMediaAttachmentInput> mediaRefs) {
        prayerPostMediaRefRepository.deleteAll(prayerPostMediaRefRepository.findByPostIdOrderBySortOrderAsc(postId));
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return;
        }
        List<PrayerPostMediaRef> refs = mediaRefs.stream()
                .map(ref -> PrayerPostMediaRef.builder()
                        .postId(postId)
                        .mediaFileId(ref.mediaFileId())
                        .usageType(ref.usageType())
                        .sortOrder(ref.sortOrder() == null ? 0 : ref.sortOrder())
                        .build())
                .toList();
        prayerPostMediaRefRepository.saveAll(refs);
    }

    private PrayerVisibilityScope toVisibilityScope(PrayerOfficialVisibilityScope visibility) {
        return switch (visibility) {
            case CHURCH -> PrayerVisibilityScope.CHURCH;
            case COMMUNITY -> PrayerVisibilityScope.COMMUNITY;
            case GLOBAL -> PrayerVisibilityScope.GLOBAL;
        };
    }

    private PrayerOfficialVisibilityScope toOfficialVisibility(PrayerVisibilityScope visibility) {
        return switch (visibility) {
            case CHURCH -> PrayerOfficialVisibilityScope.CHURCH;
            case COMMUNITY -> PrayerOfficialVisibilityScope.COMMUNITY;
            case GLOBAL -> PrayerOfficialVisibilityScope.GLOBAL;
            default -> throw new BusinessException("PRAYER_OFFICIAL_POST_INVALID_VISIBILITY");
        };
    }

    private record OfficialVisibilityContext(PrayerVisibilityScope visibility,
                                             String targetCountryCode,
                                             String targetChurchRef) {
    }
}
