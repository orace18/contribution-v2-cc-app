package com.ccapp.prayer.controller.mobile;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.prayer.dto.PrayerCreateMediaUploadRequest;
import com.ccapp.prayer.dto.PrayerCreateMediaUploadResponse;
import com.ccapp.prayer.dto.PrayerMediaUploadConfirmationResponse;
import com.ccapp.prayer.service.PrayerMediaUploadService;
import com.ccapp.prayer.service.PrayerMobileService;
import com.ccapp.media.domain.enums.MediaStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrayerMediaControllerContractTest {

    @Mock
    private PrayerMobileService prayerMobileService;
    @Mock
    private PrayerMediaUploadService prayerMediaUploadService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadUrlEndpoint_shouldExposePrayerMediaContract() {
        UUID accountHolderId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedPrincipal.builder()
                        .userId(accountHolderId)
                        .accountHolderId(accountHolderId)
                        .build(),
                null,
                List.of()
        ));

        PrayerCreateMediaUploadResponse payload = new PrayerCreateMediaUploadResponse(
                UUID.randomUUID(),
                "https://upload.test/prayer",
                OffsetDateTime.parse("2026-04-07T12:00:00Z"),
                "cc-media-public",
                "account-holders/" + accountHolderId + "/prayer-post/file.jpg"
        );
        when(prayerMediaUploadService.createPrayerPostUploadUrl(
                eq(accountHolderId),
                eq(accountHolderId),
                eq(new PrayerCreateMediaUploadRequest("image/jpeg", 1024L, "file.jpg"))
        )).thenReturn(payload);

        PrayerMobileController controller = new PrayerMobileController(prayerMobileService, prayerMediaUploadService);
        ResponseEntity<CommonResponse<PrayerCreateMediaUploadResponse>> response = controller.createPrayerMediaUploadUrl(
                new PrayerCreateMediaUploadRequest("image/jpeg", 1024L, "file.jpg")
        );

        assertEquals("Prayer media upload URL generated", response.getBody().message());
        assertEquals(payload.mediaFileId(), response.getBody().data().mediaFileId());
        assertEquals(payload.uploadUrl(), response.getBody().data().uploadUrl());
    }

    @Test
    void confirmUploadEndpoint_shouldExposePrayerMediaConfirmationContract() {
        UUID accountHolderId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedPrincipal.builder()
                        .userId(accountHolderId)
                        .accountHolderId(accountHolderId)
                        .build(),
                null,
                List.of()
        ));

        PrayerMediaUploadConfirmationResponse payload = new PrayerMediaUploadConfirmationResponse(
                mediaId,
                "image/jpeg",
                1024L,
                MediaStatus.READY,
                OffsetDateTime.parse("2026-04-07T12:00:00Z")
        );
        when(prayerMediaUploadService.confirmPrayerPostUpload(accountHolderId, accountHolderId, mediaId))
                .thenReturn(payload);

        PrayerMobileController controller = new PrayerMobileController(prayerMobileService, prayerMediaUploadService);
        ResponseEntity<CommonResponse<PrayerMediaUploadConfirmationResponse>> response = controller.confirmPrayerMediaUpload(mediaId);

        assertEquals("Prayer media upload confirmed", response.getBody().message());
        assertEquals(MediaStatus.READY, response.getBody().data().status());
        assertEquals(mediaId, response.getBody().data().mediaFileId());
    }
}
