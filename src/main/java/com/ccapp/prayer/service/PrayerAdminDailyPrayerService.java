package com.ccapp.prayer.service;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerDailyMediaRef;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.dto.AdminCreateDailyPrayerRequest;
import com.ccapp.prayer.dto.AdminDailyPrayerResponse;
import com.ccapp.prayer.dto.AdminUpdateDailyPrayerRequest;
import com.ccapp.prayer.dto.PrayerMediaAttachmentInput;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import com.ccapp.prayer.repository.PrayerDailyMediaRefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerAdminDailyPrayerService {

    private static final Set<AdminRole> WRITE_ROLES = Set.of(
            AdminRole.ROLE_SUPER_ADMIN,
            AdminRole.ROLE_COUNTRY_ADMIN,
            AdminRole.ROLE_OPERATIONS
    );

    private final PrayerDailyContentRepository prayerDailyContentRepository;
    private final PrayerDailyMediaRefRepository prayerDailyMediaRefRepository;
    private final PrayerDailyMapper prayerDailyMapper;
    private final MediaFileRepository mediaFileRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminDailyPrayerResponse> listDailyPrayers(int page,
                                                                   int size,
                                                                   LocalDate fromDate,
                                                                   LocalDate toDate,
                                                                   PrayerDailyPrayerStatus status) {
        List<PrayerDailyContent> filtered = prayerDailyContentRepository.findAll(Sort.by(
                        Sort.Order.desc("prayerDate"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )).stream()
                .filter(content -> fromDate == null || !content.getPrayerDate().isBefore(fromDate))
                .filter(content -> toDate == null || !content.getPrayerDate().isAfter(toDate))
                .filter(content -> status == null || content.getStatus() == status)
                .toList();

        PageRequest pageRequest = PageRequest.of(page, size);
        int fromIndex = Math.min((int) pageRequest.getOffset(), filtered.size());
        int toIndex = Math.min(fromIndex + pageRequest.getPageSize(), filtered.size());
        List<AdminDailyPrayerResponse> items = filtered.subList(fromIndex, toIndex).stream()
                .map(prayerDailyMapper::toAdminResponse)
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageRequest, filtered.size()));
    }

    @Transactional
    public AdminDailyPrayerResponse createDailyPrayer(UUID adminUserId, AdminCreateDailyPrayerRequest request) {
        assertDailyWriteAllowed();
        validatePrayerDateAvailable(request.prayerDate(), null);
        validateContentRequired(request.content());
        validateMediaRefs(request.mediaRefs());

        PrayerDailyContent saved = prayerDailyContentRepository.save(PrayerDailyContent.builder()
                .prayerDate(request.prayerDate())
                .title(normalizeNullable(request.title()))
                .scriptureReference(normalizeNullable(request.scriptureReference()))
                .content(request.content().trim())
                .status(PrayerDailyPrayerStatus.DRAFT)
                .createdByAdminUserId(adminUserId)
                .build());
        replaceMediaRefs(saved.getId(), request.mediaRefs());

        log.info("Prayer daily draft created dailyPrayerId={} adminUserId={} prayerDate={}",
                saved.getId(), adminUserId, saved.getPrayerDate());
        return prayerDailyMapper.toAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public AdminDailyPrayerResponse getDailyPrayer(UUID dailyPrayerId) {
        return prayerDailyMapper.toAdminResponse(findDailyPrayer(dailyPrayerId));
    }

    @Transactional
    public AdminDailyPrayerResponse updateDailyPrayer(UUID adminUserId,
                                                      UUID dailyPrayerId,
                                                      AdminUpdateDailyPrayerRequest request) {
        assertDailyWriteAllowed();
        PrayerDailyContent dailyPrayer = findDailyPrayer(dailyPrayerId);
        if (dailyPrayer.getStatus() != PrayerDailyPrayerStatus.DRAFT) {
            throw new ConflictException("PRAYER_DAILY_UPDATE_NOT_ALLOWED");
        }

        LocalDate requestedPrayerDate = request.prayerDate() == null ? dailyPrayer.getPrayerDate() : request.prayerDate();
        validatePrayerDateAvailable(requestedPrayerDate, dailyPrayerId);
        if (request.content() != null) {
            validateContentRequired(request.content());
        }
        validateMediaRefs(request.mediaRefs());

        dailyPrayer.setPrayerDate(requestedPrayerDate);
        if (request.title() != null) {
            dailyPrayer.setTitle(normalizeNullable(request.title()));
        }
        if (request.scriptureReference() != null) {
            dailyPrayer.setScriptureReference(normalizeNullable(request.scriptureReference()));
        }
        if (request.content() != null) {
            dailyPrayer.setContent(request.content().trim());
        }

        PrayerDailyContent saved = prayerDailyContentRepository.save(dailyPrayer);
        if (request.mediaRefs() != null) {
            replaceMediaRefs(saved.getId(), request.mediaRefs());
        }

        log.info("Prayer daily draft updated dailyPrayerId={} adminUserId={} prayerDate={}",
                saved.getId(), adminUserId, saved.getPrayerDate());
        return prayerDailyMapper.toAdminResponse(saved);
    }

    @Transactional
    public AdminDailyPrayerResponse publishDailyPrayer(UUID adminUserId, UUID dailyPrayerId) {
        assertDailyWriteAllowed();
        PrayerDailyContent dailyPrayer = findDailyPrayer(dailyPrayerId);
        if (dailyPrayer.getStatus() != PrayerDailyPrayerStatus.DRAFT) {
            throw new ConflictException("PRAYER_DAILY_PUBLISH_NOT_ALLOWED");
        }

        dailyPrayer.setStatus(PrayerDailyPrayerStatus.PUBLISHED);
        dailyPrayer.setPublishedAt(OffsetDateTime.now());
        PrayerDailyContent saved = prayerDailyContentRepository.save(dailyPrayer);

        log.info("Prayer daily record published dailyPrayerId={} adminUserId={}", saved.getId(), adminUserId);
        return prayerDailyMapper.toAdminResponse(saved);
    }

    @Transactional
    public AdminDailyPrayerResponse archiveDailyPrayer(UUID adminUserId, UUID dailyPrayerId) {
        assertDailyWriteAllowed();
        PrayerDailyContent dailyPrayer = findDailyPrayer(dailyPrayerId);
        if (dailyPrayer.getStatus() == PrayerDailyPrayerStatus.ARCHIVED) {
            throw new ConflictException("PRAYER_DAILY_ARCHIVE_NOT_ALLOWED");
        }

        dailyPrayer.setStatus(PrayerDailyPrayerStatus.ARCHIVED);
        PrayerDailyContent saved = prayerDailyContentRepository.save(dailyPrayer);

        log.info("Prayer daily record archived dailyPrayerId={} adminUserId={}", saved.getId(), adminUserId);
        return prayerDailyMapper.toAdminResponse(saved);
    }

    private PrayerDailyContent findDailyPrayer(UUID dailyPrayerId) {
        return prayerDailyContentRepository.findById(dailyPrayerId)
                .orElseThrow(() -> new NotFoundException("PRAYER_DAILY_NOT_FOUND"));
    }

    private void assertDailyWriteAllowed() {
        if (!WRITE_ROLES.contains(AdminSecurityUtils.currentRole())) {
            throw new AccessDeniedException("PRAYER_DAILY_WRITE_FORBIDDEN");
        }
    }

    private void validatePrayerDateAvailable(LocalDate prayerDate, UUID currentId) {
        prayerDailyContentRepository.findByPrayerDate(prayerDate)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("PRAYER_DAILY_DATE_ALREADY_EXISTS");
                });
    }

    private void validateContentRequired(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("PRAYER_DAILY_CONTENT_REQUIRED");
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

    private void replaceMediaRefs(UUID dailyPrayerId, List<PrayerMediaAttachmentInput> mediaRefs) {
        prayerDailyMediaRefRepository.deleteAll(prayerDailyMediaRefRepository.findByDailyPrayerIdOrderBySortOrderAsc(dailyPrayerId));
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return;
        }

        List<PrayerDailyMediaRef> refs = mediaRefs.stream()
                .map(ref -> PrayerDailyMediaRef.builder()
                        .dailyPrayerId(dailyPrayerId)
                        .mediaFileId(ref.mediaFileId())
                        .usageType(ref.usageType())
                        .sortOrder(ref.sortOrder() == null ? 0 : ref.sortOrder())
                        .build())
                .toList();
        prayerDailyMediaRefRepository.saveAll(refs);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
