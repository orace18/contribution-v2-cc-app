package com.ccapp.prayer.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.prayer.domain.entity.PrayerAmen;
import com.ccapp.prayer.domain.entity.PrayerComment;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.entity.PrayerPostMediaRef;
import com.ccapp.prayer.domain.entity.PrayerSupport;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.domain.enums.PrayerInteractionType;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerSupportType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.ccapp.prayer.dto.CreatePrayerCommentRequest;
import com.ccapp.prayer.dto.CreatePrayerRequest;
import com.ccapp.prayer.dto.DailyPrayerCompletionResponse;
import com.ccapp.prayer.dto.DailyPrayerResponse;
import com.ccapp.prayer.dto.PrayerCommentResponse;
import com.ccapp.prayer.dto.PrayerDashboardResponse;
import com.ccapp.prayer.dto.PrayerInteractionResponse;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.dto.UpdatePrayerRequest;
import com.ccapp.prayer.repository.PrayerAmenRepository;
import com.ccapp.prayer.repository.PrayerCommentRepository;
import com.ccapp.prayer.repository.PrayerPostMediaRefRepository;
import com.ccapp.prayer.repository.PrayerPostRepository;
import com.ccapp.prayer.repository.PrayerSupportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerMobileService {

    private final PrayerPostRepository prayerPostRepository;
    private final PrayerPostMediaRefRepository prayerPostMediaRefRepository;
    private final PrayerAmenRepository prayerAmenRepository;
    private final PrayerSupportRepository prayerSupportRepository;
    private final PrayerCommentRepository prayerCommentRepository;
    private final PrayerPostMapper prayerPostMapper;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final MediaFileRepository mediaFileRepository;
    private final PrayerEngagementService prayerEngagementService;
    private final PrayerNotificationPublisher prayerNotificationPublisher;

    @Transactional(readOnly = true)
    public PageResponse<PrayerPostResponse> getPrayerFeed(UUID actorUserId,
                                                          UUID accountHolderId,
                                                          int page,
                                                          int size,
                                                          boolean includeMinePrivate) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);

        List<PrayerPost> visiblePosts = prayerPostRepository.findAll().stream()
                .filter(post -> post.getLifecycleStatus() == PrayerPostLifecycleStatus.PUBLISHED)
                .filter(post -> isVisibleToViewer(post, viewer, includeMinePrivate))
                .sorted(feedComparator(viewer))
                .toList();

        PageRequest pageRequest = PageRequest.of(page, size);
        int fromIndex = Math.min((int) pageRequest.getOffset(), visiblePosts.size());
        int toIndex = Math.min(fromIndex + pageRequest.getPageSize(), visiblePosts.size());
        List<PrayerPostResponse> items = visiblePosts.subList(fromIndex, toIndex).stream()
                .map(post -> prayerPostMapper.toMobileResponse(post, accountHolderId))
                .toList();

        log.info("Prayer feed loaded actorUserId={} accountHolderId={} page={} size={} results={}",
                actorUserId, accountHolderId, page, size, items.size());

        return PageResponse.from(new PageImpl<>(items, pageRequest, visiblePosts.size()));
    }

    @Transactional
    public PrayerPostResponse createPrayerRequest(UUID actorUserId,
                                                  UUID accountHolderId,
                                                  CreatePrayerRequest request) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        AccountHolder author = accountHolderService.getById(accountHolderId);

        if (request.clientRequestId() != null) {
            PrayerPost existing = prayerPostRepository.findByAuthorAccountHolderIdAndClientRequestId(
                            accountHolderId,
                            request.clientRequestId()
                    )
                    .orElse(null);
            if (existing != null) {
                log.info("Prayer request create reused existing postId={} accountHolderId={} clientRequestId={}",
                        existing.getId(), accountHolderId, request.clientRequestId());
                return prayerPostMapper.toMobileResponse(existing, accountHolderId);
            }
        }

        PrayerVisibilityContext visibilityContext = resolveRequestVisibility(author, request.visibility());
        validateOwnedMediaRefs(accountHolderId, request.mediaRefs());

        PrayerPost saved = prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.REQUEST)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .authorAccountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .content(request.content().trim())
                .visibility(request.visibility())
                .audienceCircleId(visibilityContext.audienceCircleId())
                .audienceChurchRef(visibilityContext.audienceChurchRef())
                .audienceCountryCode(visibilityContext.audienceCountryCode())
                .clientRequestId(request.clientRequestId())
                .publishedAt(OffsetDateTime.now())
                .build());

        replaceMediaRefs(saved.getId(), request.mediaRefs());

        log.info("Prayer request created postId={} accountHolderId={} visibility={}",
                saved.getId(), accountHolderId, saved.getVisibility());
        return prayerPostMapper.toMobileResponse(saved, accountHolderId);
    }

    @Transactional(readOnly = true)
    public PrayerPostResponse getPrayerPost(UUID actorUserId, UUID accountHolderId, UUID postId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        PrayerPost post = prayerPostRepository.findById(postId)
                .filter(candidate -> isVisibleToViewer(candidate, viewer, true))
                .orElseThrow(() -> new NotFoundException("PRAYER_POST_NOT_FOUND"));

        return prayerPostMapper.toMobileResponse(post, accountHolderId);
    }

    @Transactional
    public PrayerPostResponse updatePrayerRequest(UUID actorUserId,
                                                  UUID accountHolderId,
                                                  UUID postId,
                                                  UpdatePrayerRequest request) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        AccountHolder owner = accountHolderService.getById(accountHolderId);
        PrayerPost post = prayerPostRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("PRAYER_POST_NOT_FOUND"));

        assertMutableRequestOwnership(post, accountHolderId);
        if (post.getLifecycleStatus() != PrayerPostLifecycleStatus.PUBLISHED) {
            throw new ConflictException("PRAYER_POST_UPDATE_NOT_ALLOWED");
        }

        PrayerVisibilityContext visibilityContext = resolveRequestVisibility(owner, request.visibility());
        validateOwnedMediaRefs(accountHolderId, request.mediaRefs());

        post.setContent(request.content().trim());
        post.setVisibility(request.visibility());
        post.setAudienceCircleId(visibilityContext.audienceCircleId());
        post.setAudienceChurchRef(visibilityContext.audienceChurchRef());
        post.setAudienceCountryCode(visibilityContext.audienceCountryCode());

        PrayerPost saved = prayerPostRepository.save(post);
        replaceMediaRefs(saved.getId(), request.mediaRefs());

        log.info("Prayer request updated postId={} accountHolderId={} visibility={}",
                postId, accountHolderId, saved.getVisibility());
        return prayerPostMapper.toMobileResponse(saved, accountHolderId);
    }

    @Transactional
    public PrayerPostResponse archivePrayerRequest(UUID actorUserId, UUID accountHolderId, UUID postId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        accountHolderService.getById(accountHolderId);
        PrayerPost post = prayerPostRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("PRAYER_POST_NOT_FOUND"));

        assertMutableRequestOwnership(post, accountHolderId);
        if (post.getLifecycleStatus() != PrayerPostLifecycleStatus.PUBLISHED) {
            throw new ConflictException("PRAYER_POST_ARCHIVE_NOT_ALLOWED");
        }

        post.setLifecycleStatus(PrayerPostLifecycleStatus.ARCHIVED);
        post.setArchivedAt(OffsetDateTime.now());
        PrayerPost saved = prayerPostRepository.save(post);

        log.info("Prayer request archived postId={} accountHolderId={}", postId, accountHolderId);
        return prayerPostMapper.toMobileResponse(saved, accountHolderId);
    }

    @Transactional
    public PrayerInteractionResponse recordAmen(UUID actorUserId, UUID accountHolderId, UUID postId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        PrayerPost post = findVisiblePostForWrite(postId, viewer);

        PrayerAmen existing = prayerAmenRepository.findByPostIdAndAccountHolderId(postId, accountHolderId).orElse(null);
        if (existing != null) {
            return buildInteractionResponse(post, PrayerInteractionType.AMEN, false, existing.getCreatedAt(), accountHolderId);
        }

        PrayerAmen saved = prayerAmenRepository.save(PrayerAmen.builder()
                .postId(postId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .build());
        post.setAmenCount(post.getAmenCount() + 1);
        prayerPostRepository.save(post);

        log.info("Prayer amen recorded postId={} accountHolderId={} created=true totalCount={}",
                postId, accountHolderId, post.getAmenCount());
        return buildInteractionResponse(post, PrayerInteractionType.AMEN, true, saved.getCreatedAt(), accountHolderId);
    }

    @Transactional
    public PrayerInteractionResponse recordSupport(UUID actorUserId, UUID accountHolderId, UUID postId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        PrayerPost post = findVisiblePostForWrite(postId, viewer);

        if (post.getPostType() != PrayerPostType.REQUEST) {
            throw new BusinessException("PRAYER_SUPPORT_NOT_ALLOWED");
        }

        PrayerSupport existing = prayerSupportRepository.findByPostIdAndAccountHolderId(postId, accountHolderId).orElse(null);
        if (existing != null) {
            return buildInteractionResponse(post, PrayerInteractionType.SUPPORT, false, existing.getCreatedAt(), accountHolderId);
        }

        PrayerSupport saved = prayerSupportRepository.save(PrayerSupport.builder()
                .postId(postId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .supportType(PrayerSupportType.PRAYED)
                .build());
        post.setSupportCount(post.getSupportCount() + 1);
        prayerPostRepository.save(post);

        prayerEngagementService.recordTrackedMissionAction(viewer, PrayerDailyMissionCode.SUPPORT_ONE_REQUEST);
        prayerNotificationPublisher.publishSupportReceived(accountHolderId, displayName(viewer), post);

        log.info("Prayer support recorded postId={} accountHolderId={} created=true totalCount={}",
                postId, accountHolderId, post.getSupportCount());
        return buildInteractionResponse(post, PrayerInteractionType.SUPPORT, true, saved.getCreatedAt(), accountHolderId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PrayerCommentResponse> listPrayerComments(UUID actorUserId,
                                                                  UUID accountHolderId,
                                                                  UUID postId,
                                                                  int page,
                                                                  int size) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        findVisiblePostForRead(postId, viewer);

        List<PrayerCommentResponse> comments = prayerCommentRepository.findByPostIdOrderByCreatedAtAscIdAsc(postId).stream()
                .map(prayerPostMapper::toCommentResponse)
                .toList();

        PageRequest pageRequest = PageRequest.of(page, size);
        int fromIndex = Math.min((int) pageRequest.getOffset(), comments.size());
        int toIndex = Math.min(fromIndex + pageRequest.getPageSize(), comments.size());
        List<PrayerCommentResponse> items = comments.subList(fromIndex, toIndex);

        log.info("Prayer comments listed postId={} accountHolderId={} page={} size={} results={}",
                postId, accountHolderId, page, size, items.size());

        return PageResponse.from(new PageImpl<>(items, pageRequest, comments.size()));
    }

    @Transactional
    public PrayerCommentResponse createPrayerComment(UUID actorUserId,
                                                     UUID accountHolderId,
                                                     UUID postId,
                                                     CreatePrayerCommentRequest request) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);

        if (request.clientRequestId() != null) {
            PrayerComment existing = prayerCommentRepository.findByAccountHolderIdAndClientRequestId(accountHolderId, request.clientRequestId())
                    .orElse(null);
            if (existing != null) {
                if (!existing.getPostId().equals(postId)) {
                    throw new ConflictException("PRAYER_COMMENT_CLIENT_REQUEST_ID_CONFLICT");
                }
                findVisiblePostForRead(postId, viewer);
                return prayerPostMapper.toCommentResponse(existing);
            }
        }

        PrayerPost post = findVisiblePostForWrite(postId, viewer);
        PrayerComment saved = prayerCommentRepository.save(PrayerComment.builder()
                .postId(postId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .clientRequestId(request.clientRequestId())
                .content(request.content().trim())
                .build());
        post.setCommentCount(post.getCommentCount() + 1);
        prayerPostRepository.save(post);

        prayerEngagementService.recordTrackedMissionAction(viewer, PrayerDailyMissionCode.COMMENT_ONE_POST);
        prayerNotificationPublisher.publishCommentReceived(accountHolderId, displayName(viewer), post);

        log.info("Prayer comment created postId={} accountHolderId={} commentId={} totalCount={}",
                postId, accountHolderId, saved.getId(), post.getCommentCount());
        return prayerPostMapper.toCommentResponse(saved);
    }

    @Transactional(readOnly = true)
    public DailyPrayerResponse getCurrentDailyPrayer(UUID actorUserId, UUID accountHolderId, String timezone) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        return prayerEngagementService.getCurrentDailyPrayer(viewer, timezone);
    }

    @Transactional
    public DailyPrayerCompletionResponse completeCurrentDailyPrayer(UUID actorUserId,
                                                                    UUID accountHolderId,
                                                                    String timezone,
                                                                    PrayerDailyPrayerResponseType responseType) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        return prayerEngagementService.completeCurrentDailyPrayer(actorUserId, viewer, timezone, responseType);
    }

    @Transactional
    public PrayerDashboardResponse getPrayerDashboard(UUID actorUserId, UUID accountHolderId, String timezone) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        return prayerEngagementService.getPrayerDashboard(viewer, timezone);
    }

    /**
     * Temporary compatibility bridge for the deprecated legacy community unlike route.
     */
    @Transactional
    public void removeAmenCompatibility(UUID actorUserId, UUID accountHolderId, UUID postId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        AccountHolder viewer = accountHolderService.getById(accountHolderId);
        PrayerPost post = findVisiblePostForWrite(postId, viewer);

        PrayerAmen existing = prayerAmenRepository.findByPostIdAndAccountHolderId(postId, accountHolderId).orElse(null);
        if (existing == null) {
            log.info("Prayer amen compatibility remove no-op postId={} accountHolderId={}", postId, accountHolderId);
            return;
        }

        prayerAmenRepository.delete(existing);
        if (post.getAmenCount() > 0) {
            post.setAmenCount(post.getAmenCount() - 1);
        }
        prayerPostRepository.save(post);
        log.info("Prayer amen compatibility removed postId={} accountHolderId={} totalCount={}",
                postId, accountHolderId, post.getAmenCount());
    }

    private boolean isVisibleToViewer(PrayerPost post, AccountHolder viewer, boolean includeMinePrivate) {
        if (post.getLifecycleStatus() != PrayerPostLifecycleStatus.PUBLISHED) {
            return false;
        }

        return switch (post.getVisibility()) {
            case PRIVATE -> includeMinePrivate
                    && post.getAuthorAccountHolderId() != null
                    && post.getAuthorAccountHolderId().equals(viewer.getId());
            case CIRCLE -> post.getAudienceCircleId() != null
                    && circleMemberRepository.existsByCircle_IdAndAccountHolderId(post.getAudienceCircleId(), viewer.getId());
            case CHURCH -> false;
            case COMMUNITY -> viewer.getServiceCountryCode() != null
                    && post.getAudienceCountryCode() != null
                    && viewer.getServiceCountryCode().equalsIgnoreCase(post.getAudienceCountryCode());
            case GLOBAL -> true;
        };
    }

    private Comparator<PrayerPost> feedComparator(AccountHolder viewer) {
        return Comparator
                .comparingInt((PrayerPost post) -> scopePriority(post, viewer))
                .thenComparing(PrayerPost::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PrayerPost::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int scopePriority(PrayerPost post, AccountHolder viewer) {
        return switch (post.getVisibility()) {
            case PRIVATE -> 0;
            case CIRCLE -> 1;
            case CHURCH -> 2;
            case COMMUNITY -> 3;
            case GLOBAL -> 4;
        };
    }

    private PrayerVisibilityContext resolveRequestVisibility(AccountHolder owner, PrayerVisibilityScope visibility) {
        return switch (visibility) {
            case PRIVATE, GLOBAL -> new PrayerVisibilityContext(null, null, null);
            case COMMUNITY -> {
                if (owner.getServiceCountryCode() == null || owner.getServiceCountryCode().isBlank()) {
                    throw new BusinessException("PRAYER_COMMUNITY_SCOPE_REQUIRES_SERVICE_COUNTRY");
                }
                yield new PrayerVisibilityContext(null, null, owner.getServiceCountryCode().trim().toUpperCase());
            }
            case CIRCLE -> {
                Circle circle = circleRepository.findActiveCirclesForUser(owner.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("PRAYER_CIRCLE_CONTEXT_REQUIRED"));
                yield new PrayerVisibilityContext(circle.getId(), null, null);
            }
            case CHURCH -> throw new BusinessException("PRAYER_CHURCH_SCOPE_NOT_AVAILABLE");
        };
    }

    private void assertMutableRequestOwnership(PrayerPost post, UUID accountHolderId) {
        if (post.getPostType() != PrayerPostType.REQUEST) {
            throw new AccessDeniedException("PRAYER_POST_UPDATE_FORBIDDEN");
        }
        if (post.getAuthorAccountHolderId() == null || !post.getAuthorAccountHolderId().equals(accountHolderId)) {
            throw new AccessDeniedException("PRAYER_POST_UPDATE_FORBIDDEN");
        }
        if (post.getLifecycleStatus() == PrayerPostLifecycleStatus.MODERATED
                || post.getLifecycleStatus() == PrayerPostLifecycleStatus.DELETED) {
            throw new ConflictException("PRAYER_POST_UPDATE_NOT_ALLOWED");
        }
    }

    private void validateOwnedMediaRefs(UUID accountHolderId, List<com.ccapp.prayer.dto.PrayerMediaAttachmentInput> mediaRefs) {
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return;
        }
        for (com.ccapp.prayer.dto.PrayerMediaAttachmentInput ref : mediaRefs) {
            mediaFileRepository.findByIdAndAccountHolderId(ref.mediaFileId(), accountHolderId)
                    .orElseThrow(() -> new BusinessException("PRAYER_MEDIA_FILE_NOT_FOUND"));
        }
    }

    private void replaceMediaRefs(UUID postId, List<com.ccapp.prayer.dto.PrayerMediaAttachmentInput> mediaRefs) {
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

    private PrayerPost findVisiblePostForRead(UUID postId, AccountHolder viewer) {
        return prayerPostRepository.findById(postId)
                .filter(candidate -> isVisibleToViewer(candidate, viewer, true))
                .orElseThrow(() -> new NotFoundException("PRAYER_POST_NOT_FOUND"));
    }

    private PrayerPost findVisiblePostForWrite(UUID postId, AccountHolder viewer) {
        return prayerPostRepository.findByIdForUpdate(postId)
                .filter(candidate -> isVisibleToViewer(candidate, viewer, true))
                .orElseThrow(() -> new NotFoundException("PRAYER_POST_NOT_FOUND"));
    }

    private PrayerInteractionResponse buildInteractionResponse(PrayerPost post,
                                                               PrayerInteractionType interactionType,
                                                               boolean created,
                                                               OffsetDateTime recordedAt,
                                                               UUID viewerAccountHolderId) {
        int totalCount = switch (interactionType) {
            case AMEN -> post.getAmenCount();
            case SUPPORT -> post.getSupportCount();
        };
        return new PrayerInteractionResponse(
                post.getId(),
                interactionType,
                created,
                totalCount,
                recordedAt,
                prayerPostMapper.toViewerState(post, viewerAccountHolderId)
        );
    }

    private String displayName(AccountHolder accountHolder) {
        if (accountHolder == null) {
            return "Un membre";
        }
        String first = accountHolder.getFirstName() == null ? "" : accountHolder.getFirstName().trim();
        String last = accountHolder.getLastName() == null ? "" : accountHolder.getLastName().trim();
        String combined = (first + " " + last).trim();
        return combined.isBlank() ? "Un membre" : combined;
    }

    private record PrayerVisibilityContext(UUID audienceCircleId, String audienceChurchRef, String audienceCountryCode) {
    }
}
