package com.ccapp.media.service;

import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.repository.KycDocumentRepository;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private MediaService mediaService;

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
}
