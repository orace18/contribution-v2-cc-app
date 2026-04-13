package com.ccapp.media.service;

import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.media.domain.dto.CreateUploadUrlRequest;
import com.ccapp.media.domain.dto.CreateUploadUrlResponse;
import com.ccapp.media.domain.dto.MediaFileResponse;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.repository.KycDocumentRepository;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.media.storage.PresignedUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final UUID ACTOR_USER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ACCOUNT_HOLDER_ID = UUID.randomUUID();
    private static final UUID TARGET_ACCOUNT_HOLDER_ID = UUID.randomUUID();
    private static final UUID MEDIA_ID = UUID.randomUUID();

    @Mock
    private MediaFileRepository mediaFileRepository;
    @Mock
    private KycDocumentRepository kycDocumentRepository;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private CountryContextService countryContextService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private KycMediaBackupService kycMediaBackupService;

    @InjectMocks
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "publicBucket", "cc-media-public");
        ReflectionTestUtils.setField(mediaService, "privateBucket", "cc-media-private");
        ReflectionTestUtils.setField(mediaService, "kycBucket", "cc-media-kyc");
        ReflectionTestUtils.setField(mediaService, "uploadUrlTtlMinutes", 15L);
        ReflectionTestUtils.setField(mediaService, "downloadUrlTtlMinutes", 15L);
        ReflectionTestUtils.setField(mediaService, "maxSizeBytes", 10_485_760L);
    }

    @Test
    void proxyUpload_shouldResolveManagedTargetFromMediaOwner() {
        MediaFile media = MediaFile.builder()
                .id(MEDIA_ID)
                .accountHolderId(TARGET_ACCOUNT_HOLDER_ID)
                .type(MediaType.KYC_IDENTITY_FRONT)
                .bucket("cc-media-kyc")
                .objectKey("account-holders/" + TARGET_ACCOUNT_HOLDER_ID + "/front.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .status(MediaStatus.PENDING_UPLOAD)
                .createdAt(OffsetDateTime.now())
                .build();

        when(mediaFileRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));
        when(countryContextService.resolve(ACTOR_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(TARGET_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycDocumentRepository.findByAccountHolderId(TARGET_ACCOUNT_HOLDER_ID)).thenReturn(List.of());

        mediaService.proxyUpload(
                ACTOR_USER_ID,
                ACTOR_ACCOUNT_HOLDER_ID,
                MEDIA_ID,
                new byte[]{1, 2, 3},
                "image/jpeg"
        );

        assertEquals(MediaStatus.READY, media.getStatus());
        verify(accessControlService).assertCanActOnAccountHolder(
                ACTOR_USER_ID,
                TARGET_ACCOUNT_HOLDER_ID,
                com.ccapp.identity.delegation.domain.enums.PermissionLevel.FULL
        );
        verify(objectStorageService).putObject(
                eq("cc-media-kyc"),
                eq("account-holders/" + TARGET_ACCOUNT_HOLDER_ID + "/front.jpg"),
                any(byte[].class),
                eq("image/jpeg")
        );
        verify(mediaFileRepository).save(media);
    }

    @Test
    void proxyUpload_shouldRejectPreviousActiveDocumentForSameSlot() {
        MediaFile media = MediaFile.builder()
                .id(MEDIA_ID)
                .accountHolderId(TARGET_ACCOUNT_HOLDER_ID)
                .type(MediaType.KYC_IDENTITY_FRONT)
                .bucket("cc-media-kyc")
                .objectKey("account-holders/" + TARGET_ACCOUNT_HOLDER_ID + "/front.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .status(MediaStatus.PENDING_UPLOAD)
                .createdAt(OffsetDateTime.now())
                .build();
        KycDocument previousDocument = KycDocument.builder()
                .id(UUID.randomUUID())
                .accountHolderId(TARGET_ACCOUNT_HOLDER_ID)
                .mediaFileId(UUID.randomUUID())
                .documentType(KycDocumentType.IDENTITY_FRONT)
                .status(KycDocumentStatus.SUBMITTED)
                .createdAt(OffsetDateTime.now().minusDays(1))
                .build();

        when(mediaFileRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));
        when(countryContextService.resolve(ACTOR_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(TARGET_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycDocumentRepository.findByAccountHolderId(TARGET_ACCOUNT_HOLDER_ID)).thenReturn(List.of(previousDocument));

        mediaService.proxyUpload(
                ACTOR_USER_ID,
                ACTOR_ACCOUNT_HOLDER_ID,
                MEDIA_ID,
                new byte[]{1, 2, 3},
                "image/jpeg"
        );

        assertEquals(KycDocumentStatus.REJECTED, previousDocument.getStatus());
        verify(kycDocumentRepository).saveAll(List.of(previousDocument));
        verify(kycDocumentRepository).save(any(KycDocument.class));
    }

    @Test
    void createUploadUrl_shouldRemapLegacyFallbackForKycMediaToProxyUploadUrl() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);
        CreateUploadUrlRequest request = new CreateUploadUrlRequest(
                MediaType.KYC_IDENTITY_FRONT,
                "image/jpeg",
                1024L,
                "front.jpg",
                TARGET_ACCOUNT_HOLDER_ID
        );

        when(countryContextService.resolve(ACTOR_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(TARGET_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(accountHolderService.getById(TARGET_ACCOUNT_HOLDER_ID)).thenReturn(null);
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            media.setId(MEDIA_ID);
            return media;
        });
        when(objectStorageService.createUploadUrl(eq("cc-media-kyc"), any(String.class), eq("image/jpeg"), any()))
                .thenReturn(new PresignedUrl(
                        "https://api-gateway.clubchretien.org/cc-backend-monolitic/dev-media/7aecbbfe-98c8-38e1-baa0-aea2f16d92bf",
                        expiresAt
                ));

        CreateUploadUrlResponse response = mediaService.createUploadUrl(
                ACTOR_USER_ID,
                ACTOR_ACCOUNT_HOLDER_ID,
                request
        );

        assertEquals(MEDIA_ID, response.mediaId());
        assertEquals(
                "https://kyc-upload.internal/api/media/" + MEDIA_ID + "/upload-proxy",
                response.uploadUrl()
        );
    }

    @Test
    void getMedia_shouldSuppressLegacyFallbackPublicUrlForKycMedia() {
        MediaFile media = MediaFile.builder()
                .id(MEDIA_ID)
                .accountHolderId(TARGET_ACCOUNT_HOLDER_ID)
                .type(MediaType.KYC_IDENTITY_FRONT)
                .bucket("cc-media-kyc")
                .objectKey("account-holders/" + TARGET_ACCOUNT_HOLDER_ID + "/kyc/front.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .status(MediaStatus.READY)
                .createdAt(OffsetDateTime.now())
                .build();

        when(mediaFileRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));
        when(countryContextService.resolve(ACTOR_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(TARGET_ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(objectStorageService.publicObjectUrl(media.getBucket(), media.getObjectKey()))
                .thenReturn("https://api-gateway.clubchretien.org/cc-backend-monolitic/dev-media/b3dbfa81-21a4-3f48-99fa-ee74667a98e3");

        MediaFileResponse response = mediaService.getMedia(
                ACTOR_USER_ID,
                ACTOR_ACCOUNT_HOLDER_ID,
                MEDIA_ID
        );

        assertNull(response.publicUrl());
    }
}
