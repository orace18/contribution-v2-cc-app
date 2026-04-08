package com.ccapp.prayer.service;

import com.ccapp.media.domain.dto.CreateUploadUrlRequest;
import com.ccapp.media.domain.dto.CreateUploadUrlResponse;
import com.ccapp.media.domain.dto.MediaFileResponse;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.service.MediaService;
import com.ccapp.prayer.dto.PrayerCreateMediaUploadRequest;
import com.ccapp.prayer.dto.PrayerCreateMediaUploadResponse;
import com.ccapp.prayer.dto.PrayerMediaUploadConfirmationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrayerMediaUploadServiceTest {

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private PrayerMediaUploadService prayerMediaUploadService;

    @Test
    void createPrayerPostUploadUrl_shouldUsePrayerPostMediaType() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-04-07T12:00:00Z");
        when(mediaService.createUploadUrl(eq(actorUserId), eq(accountHolderId), org.mockito.ArgumentMatchers.any(CreateUploadUrlRequest.class)))
                .thenReturn(new CreateUploadUrlResponse(
                        UUID.randomUUID(),
                        "cc-media-public",
                        "account-holders/" + accountHolderId + "/prayer-post/file.jpg",
                        "https://upload.test/prayer",
                        expiresAt
                ));

        PrayerCreateMediaUploadResponse response = prayerMediaUploadService.createPrayerPostUploadUrl(
                actorUserId,
                accountHolderId,
                new PrayerCreateMediaUploadRequest("image/jpeg", 1024L, "file.jpg")
        );

        ArgumentCaptor<CreateUploadUrlRequest> requestCaptor = ArgumentCaptor.forClass(CreateUploadUrlRequest.class);
        verify(mediaService).createUploadUrl(eq(actorUserId), eq(accountHolderId), requestCaptor.capture());
        assertEquals(MediaType.PRAYER_POST, requestCaptor.getValue().type());
        assertEquals("https://upload.test/prayer", response.uploadUrl());
        assertEquals(expiresAt, response.expiresAt());
    }

    @Test
    void confirmPrayerPostUpload_shouldMapReadyResponse() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(mediaService.confirmUpload(eq(actorUserId), eq(accountHolderId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new MediaFileResponse(
                        mediaId,
                        accountHolderId,
                        MediaType.PRAYER_POST,
                        "cc-media-public",
                        "account-holders/" + accountHolderId + "/prayer-post/file.jpg",
                        "https://cdn.test/prayer/file.jpg",
                        "image/jpeg",
                        1024L,
                        MediaStatus.READY,
                        OffsetDateTime.parse("2026-04-07T12:00:00Z")
                ));

        PrayerMediaUploadConfirmationResponse response = prayerMediaUploadService.confirmPrayerPostUpload(
                actorUserId,
                accountHolderId,
                mediaId
        );

        assertEquals(mediaId, response.mediaFileId());
        assertEquals(MediaStatus.READY, response.status());
        assertEquals("image/jpeg", response.contentType());
    }
}
