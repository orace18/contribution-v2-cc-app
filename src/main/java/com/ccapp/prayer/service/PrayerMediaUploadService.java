package com.ccapp.prayer.service;

import com.ccapp.media.domain.dto.ConfirmUploadRequest;
import com.ccapp.media.domain.dto.CreateUploadUrlRequest;
import com.ccapp.media.domain.dto.CreateUploadUrlResponse;
import com.ccapp.media.domain.dto.MediaFileResponse;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.service.MediaService;
import com.ccapp.prayer.dto.PrayerCreateMediaUploadRequest;
import com.ccapp.prayer.dto.PrayerCreateMediaUploadResponse;
import com.ccapp.prayer.dto.PrayerMediaUploadConfirmationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrayerMediaUploadService {

    private final MediaService mediaService;

    @Transactional
    public PrayerCreateMediaUploadResponse createPrayerPostUploadUrl(UUID actorUserId,
                                                                     UUID accountHolderId,
                                                                     PrayerCreateMediaUploadRequest request) {
        CreateUploadUrlResponse uploadUrl = mediaService.createUploadUrl(
                actorUserId,
                accountHolderId,
                new CreateUploadUrlRequest(
                        MediaType.PRAYER_POST,
                        request.contentType(),
                        request.size(),
                        request.originalFileName(),
                        accountHolderId
                )
        );
        return new PrayerCreateMediaUploadResponse(
                uploadUrl.mediaId(),
                uploadUrl.uploadUrl(),
                uploadUrl.expiresAt(),
                uploadUrl.bucket(),
                uploadUrl.objectKey()
        );
    }

    @Transactional
    public PrayerMediaUploadConfirmationResponse confirmPrayerPostUpload(UUID actorUserId,
                                                                         UUID accountHolderId,
                                                                         UUID mediaFileId) {
        MediaFileResponse media = mediaService.confirmUpload(
                actorUserId,
                accountHolderId,
                new ConfirmUploadRequest(mediaFileId, accountHolderId)
        );
        return new PrayerMediaUploadConfirmationResponse(
                media.id(),
                media.contentType(),
                media.size(),
                media.status(),
                media.createdAt()
        );
    }
}
