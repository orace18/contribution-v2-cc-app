package com.ccapp.media.service;

import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.repository.KycDocumentRepository;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycDocumentResolutionServiceTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();

    @Mock
    private KycDocumentRepository kycDocumentRepository;
    @Mock
    private MediaFileRepository mediaFileRepository;
    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private KycDocumentResolutionService kycDocumentResolutionService;

    @Test
    void resolveActiveDocuments_shouldIgnoreAndPurgeMissingLegacyFallbackMedia() {
        KycDocument staleFront = buildDocument(
                KycDocumentType.IDENTITY_FRONT,
                UUID.randomUUID(),
                OffsetDateTime.now()
        );
        KycDocument validFront = buildDocument(
                KycDocumentType.IDENTITY_FRONT,
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(5)
        );
        KycDocument validBack = buildDocument(
                KycDocumentType.IDENTITY_BACK,
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(4)
        );
        KycDocument validSelfie = buildDocument(
                KycDocumentType.SELFIE,
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(3)
        );

        MediaFile staleFrontMedia = buildMediaFile(staleFront.getMediaFileId(), MediaType.KYC_IDENTITY_FRONT);
        MediaFile validFrontMedia = buildMediaFile(validFront.getMediaFileId(), MediaType.KYC_IDENTITY_FRONT);
        MediaFile validBackMedia = buildMediaFile(validBack.getMediaFileId(), MediaType.KYC_IDENTITY_BACK);
        MediaFile validSelfieMedia = buildMediaFile(validSelfie.getMediaFileId(), MediaType.KYC_SELFIE);

        when(kycDocumentRepository.findByAccountHolderId(ACCOUNT_HOLDER_ID)).thenReturn(List.of(
                staleFront,
                validFront,
                validBack,
                validSelfie
        ));
        when(mediaFileRepository.findByIdAndAccountHolderId(staleFront.getMediaFileId(), ACCOUNT_HOLDER_ID))
                .thenReturn(Optional.of(staleFrontMedia));
        when(mediaFileRepository.findByIdAndAccountHolderId(validFront.getMediaFileId(), ACCOUNT_HOLDER_ID))
                .thenReturn(Optional.of(validFrontMedia));
        when(mediaFileRepository.findByIdAndAccountHolderId(validBack.getMediaFileId(), ACCOUNT_HOLDER_ID))
                .thenReturn(Optional.of(validBackMedia));
        when(mediaFileRepository.findByIdAndAccountHolderId(validSelfie.getMediaFileId(), ACCOUNT_HOLDER_ID))
                .thenReturn(Optional.of(validSelfieMedia));

        when(objectStorageService.publicObjectUrl(staleFrontMedia.getBucket(), staleFrontMedia.getObjectKey()))
                .thenReturn("https://api.local/dev-media/" + staleFrontMedia.getId());
        when(objectStorageService.objectExists(staleFrontMedia.getBucket(), staleFrontMedia.getObjectKey()))
                .thenReturn(false);
        when(objectStorageService.publicObjectUrl(validFrontMedia.getBucket(), validFrontMedia.getObjectKey()))
                .thenReturn("https://media.local/" + validFrontMedia.getObjectKey());
        when(objectStorageService.publicObjectUrl(validBackMedia.getBucket(), validBackMedia.getObjectKey()))
                .thenReturn("https://media.local/" + validBackMedia.getObjectKey());
        when(objectStorageService.publicObjectUrl(validSelfieMedia.getBucket(), validSelfieMedia.getObjectKey()))
                .thenReturn("https://media.local/" + validSelfieMedia.getObjectKey());

        Map<KycDocumentType, KycDocumentResolutionService.ResolvedKycDocument> resolvedDocuments =
                kycDocumentResolutionService.resolveActiveDocuments(ACCOUNT_HOLDER_ID);

        assertEquals(validFront.getMediaFileId(), resolvedDocuments.get(KycDocumentType.IDENTITY_FRONT).mediaFile().getId());
        assertEquals(validBack.getMediaFileId(), resolvedDocuments.get(KycDocumentType.IDENTITY_BACK).mediaFile().getId());
        assertEquals(validSelfie.getMediaFileId(), resolvedDocuments.get(KycDocumentType.SELFIE).mediaFile().getId());
        assertEquals(KycDocumentStatus.REJECTED, staleFront.getStatus());
        assertEquals(MediaStatus.DELETED, staleFrontMedia.getStatus());

        ArgumentCaptor<List<KycDocument>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(kycDocumentRepository).saveAll(documentsCaptor.capture());
        assertTrue(documentsCaptor.getValue().contains(staleFront));

        ArgumentCaptor<List<MediaFile>> mediaFilesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mediaFileRepository).saveAll(mediaFilesCaptor.capture());
        assertTrue(mediaFilesCaptor.getValue().contains(staleFrontMedia));
    }

    private KycDocument buildDocument(KycDocumentType documentType, UUID mediaFileId, OffsetDateTime createdAt) {
        return KycDocument.builder()
                .id(UUID.randomUUID())
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .mediaFileId(mediaFileId)
                .documentType(documentType)
                .status(KycDocumentStatus.SUBMITTED)
                .createdAt(createdAt)
                .build();
    }

    private MediaFile buildMediaFile(UUID mediaId, MediaType mediaType) {
        return MediaFile.builder()
                .id(mediaId)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .type(mediaType)
                .bucket("cc-media-kyc")
                .objectKey("account-holders/" + ACCOUNT_HOLDER_ID + "/" + mediaType.name().toLowerCase() + "/" + mediaId + ".jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .status(MediaStatus.READY)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
