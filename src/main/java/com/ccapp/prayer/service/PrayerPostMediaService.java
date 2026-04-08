package com.ccapp.prayer.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.prayer.domain.entity.PrayerPostMediaRef;
import com.ccapp.prayer.domain.enums.PrayerMediaUsageType;
import com.ccapp.prayer.dto.PrayerMediaAttachmentInput;
import com.ccapp.prayer.dto.PrayerMediaRefResponse;
import com.ccapp.prayer.repository.PrayerPostMediaRefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerPostMediaService {

    private static final Set<MediaType> ALLOWED_PRAYER_MEDIA_TYPES = Set.of(
            MediaType.PRAYER_POST,
            MediaType.GENERIC_PUBLIC
    );

    private final PrayerPostMediaRefRepository prayerPostMediaRefRepository;
    private final MediaFileRepository mediaFileRepository;
    private final ObjectStorageService objectStorageService;

    @Value("${media.download-url-ttl-minutes:15}")
    private long downloadUrlTtlMinutes;

    @Transactional(readOnly = true)
    public List<PrayerMediaRefResponse> loadPostMediaRefs(UUID postId) {
        return prayerPostMediaRefRepository.findByPostIdOrderBySortOrderAsc(postId).stream()
                .map(this::toMediaRefResponse)
                .toList();
    }

    @Transactional
    public void validateOwnedAndReplace(UUID accountHolderId,
                                        UUID postId,
                                        List<PrayerMediaAttachmentInput> mediaRefs) {
        validateOwned(accountHolderId, mediaRefs);
        replaceMediaRefs(postId, mediaRefs);
    }

    @Transactional
    public void validateOfficialAndReplace(UUID postId,
                                           List<PrayerMediaAttachmentInput> mediaRefs) {
        validateOfficial(mediaRefs);
        replaceMediaRefs(postId, mediaRefs);
    }

    @Transactional(readOnly = true)
    public void validateOwned(UUID accountHolderId, List<PrayerMediaAttachmentInput> mediaRefs) {
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return;
        }

        Set<UUID> seenMediaIds = new HashSet<>();
        for (PrayerMediaAttachmentInput ref : mediaRefs) {
            ensureUniqueMediaRef(ref, seenMediaIds);
            MediaFile media = mediaFileRepository.findByIdAndAccountHolderId(ref.mediaFileId(), accountHolderId)
                    .orElseThrow(() -> new BusinessException("PRAYER_MEDIA_FILE_NOT_FOUND"));
            validateAttachableMedia(media, ref.usageType());
        }
    }

    @Transactional(readOnly = true)
    public void validateOfficial(List<PrayerMediaAttachmentInput> mediaRefs) {
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return;
        }

        Set<UUID> seenMediaIds = new HashSet<>();
        for (PrayerMediaAttachmentInput ref : mediaRefs) {
            ensureUniqueMediaRef(ref, seenMediaIds);
            MediaFile media = mediaFileRepository.findById(ref.mediaFileId())
                    .orElseThrow(() -> new BusinessException("PRAYER_MEDIA_FILE_NOT_FOUND"));
            validateAttachableMedia(media, ref.usageType());
        }
    }

    private void ensureUniqueMediaRef(PrayerMediaAttachmentInput ref, Set<UUID> seenMediaIds) {
        if (!seenMediaIds.add(ref.mediaFileId())) {
            throw new BusinessException("PRAYER_MEDIA_DUPLICATE");
        }
    }

    private void validateAttachableMedia(MediaFile media, PrayerMediaUsageType usageType) {
        if (media.getStatus() != MediaStatus.READY) {
            throw new BusinessException("PRAYER_MEDIA_NOT_READY");
        }
        if (!ALLOWED_PRAYER_MEDIA_TYPES.contains(media.getType())) {
            throw new BusinessException("PRAYER_MEDIA_INVALID");
        }
        if (!contentTypeAllowedForUsage(media.getContentType(), usageType)) {
            throw new BusinessException("PRAYER_MEDIA_INVALID");
        }
    }

    private boolean contentTypeAllowedForUsage(String contentType, PrayerMediaUsageType usageType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase();
        return switch (usageType) {
            case COVER, INLINE -> normalized.startsWith("image/") || normalized.startsWith("video/");
            case AUDIO -> normalized.startsWith("audio/");
            case ATTACHMENT -> !normalized.isBlank();
        };
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

    private PrayerMediaRefResponse toMediaRefResponse(PrayerPostMediaRef ref) {
        MediaFile mediaFile = mediaFileRepository.findById(ref.getMediaFileId()).orElse(null);
        return new PrayerMediaRefResponse(
                ref.getMediaFileId(),
                ref.getUsageType(),
                ref.getSortOrder(),
                mediaFile == null ? null : mediaFile.getContentType(),
                mediaFile == null ? null : buildDownloadUrl(mediaFile)
        );
    }

    private String buildDownloadUrl(MediaFile mediaFile) {
        if (mediaFile.getStatus() != MediaStatus.READY) {
            return null;
        }
        try {
            return objectStorageService.createDownloadUrl(
                    mediaFile.getBucket(),
                    mediaFile.getObjectKey(),
                    Duration.ofMinutes(downloadUrlTtlMinutes)
            ).url();
        } catch (Exception ex) {
            log.warn("Unable to create prayer media download URL mediaFileId={}", mediaFile.getId(), ex);
            return null;
        }
    }
}
