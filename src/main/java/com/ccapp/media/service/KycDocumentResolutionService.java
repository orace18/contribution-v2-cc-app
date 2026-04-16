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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycDocumentResolutionService {

    private final KycDocumentRepository kycDocumentRepository;
    private final MediaFileRepository mediaFileRepository;
    private final ObjectStorageService objectStorageService;
    private final KycMediaBackupService kycMediaBackupService;

    @Transactional
    public Map<KycDocumentType, ResolvedKycDocument> resolveActiveDocuments(UUID accountHolderId) {
        List<KycDocument> documents = kycDocumentRepository.findByAccountHolderId(accountHolderId);
        if (documents.isEmpty()) {
            return Map.of();
        }

        Map<KycDocumentType, List<KycDocument>> candidatesByType = documents.stream()
                .filter(this::isAcceptableForResolution)
                .sorted(Comparator.comparing(KycDocument::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.groupingBy(
                        KycDocument::getDocumentType,
                        () -> new EnumMap<>(KycDocumentType.class),
                        Collectors.toList()
                ));

        EnumMap<KycDocumentType, ResolvedKycDocument> resolved = new EnumMap<>(KycDocumentType.class);
        List<KycDocument> documentsToUpdate = new ArrayList<>();
        List<MediaFile> mediaFilesToUpdate = new ArrayList<>();

        for (KycDocumentType documentType : KycDocumentType.values()) {
            List<KycDocument> candidates = candidatesByType.getOrDefault(documentType, List.of());
            for (KycDocument candidate : candidates) {
                ResolvedKycDocument resolvedCandidate = resolveCandidate(
                        accountHolderId,
                        documentType,
                        candidate,
                        documentsToUpdate,
                        mediaFilesToUpdate
                );
                if (resolvedCandidate != null) {
                    resolved.put(documentType, resolvedCandidate);
                    break;
                }
            }
        }

        if (!documentsToUpdate.isEmpty()) {
            kycDocumentRepository.saveAll(documentsToUpdate);
        }
        if (!mediaFilesToUpdate.isEmpty()) {
            mediaFileRepository.saveAll(mediaFilesToUpdate);
        }

        return Map.copyOf(resolved);
    }

    private ResolvedKycDocument resolveCandidate(UUID accountHolderId,
                                                 KycDocumentType documentType,
                                                 KycDocument document,
                                                 List<KycDocument> documentsToUpdate,
                                                 List<MediaFile> mediaFilesToUpdate) {
        UUID mediaId = document.getMediaFileId();
        MediaFile mediaFile = mediaFileRepository.findByIdAndAccountHolderId(mediaId, accountHolderId).orElse(null);
        if (mediaFile == null) {
            log.warn(
                    "kyc media candidate ignored accountHolderId={} documentSlot={} documentId={} mediaId={} reason=MEDIA_ROW_NOT_FOUND",
                    accountHolderId,
                    documentType,
                    document.getId(),
                    mediaId
            );
            markDocumentRejected(document, documentsToUpdate);
            return null;
        }

        String finalUrl = objectStorageService.publicObjectUrl(mediaFile.getBucket(), mediaFile.getObjectKey());
        String source = isLegacyFallbackUrl(finalUrl) ? "LOCAL_FALLBACK" : "OBJECT_STORAGE";

        if (mediaFile.getStatus() != MediaStatus.READY) {
            log.warn(
                    "kyc media candidate ignored accountHolderId={} documentSlot={} documentId={} mediaId={} source={} bucket={} objectKey={} finalUrl={} reason=MEDIA_NOT_READY status={}",
                    accountHolderId,
                    documentType,
                    document.getId(),
                    mediaId,
                    source,
                    mediaFile.getBucket(),
                    mediaFile.getObjectKey(),
                    finalUrl,
                    mediaFile.getStatus()
            );
            return null;
        }

        MediaType expectedMediaType = expectedMediaType(documentType);
        if (mediaFile.getType() != expectedMediaType) {
            log.warn(
                    "kyc media candidate ignored accountHolderId={} documentSlot={} documentId={} mediaId={} source={} bucket={} objectKey={} finalUrl={} reason=MEDIA_TYPE_MISMATCH expected={} actual={}",
                    accountHolderId,
                    documentType,
                    document.getId(),
                    mediaId,
                    source,
                    mediaFile.getBucket(),
                    mediaFile.getObjectKey(),
                    finalUrl,
                    expectedMediaType,
                    mediaFile.getType()
            );
            markDocumentRejected(document, documentsToUpdate);
            return null;
        }

        if (isLegacyFallbackUrl(finalUrl) && !objectStorageService.objectExists(mediaFile.getBucket(), mediaFile.getObjectKey())) {
            KycMediaBackupService.StoredKycMediaBackup mediaBackup = findKycBackup(mediaFile).orElse(null);
            if (mediaBackup != null) {
                log.info(
                        "kyc media resolved accountHolderId={} documentSlot={} documentId={} mediaId={} source={} bucket={} objectKey={} finalUrl={} backupBytes={} documentCreatedAt={}",
                        accountHolderId,
                        documentType,
                        document.getId(),
                        mediaId,
                        "DATABASE_BACKUP",
                        mediaFile.getBucket(),
                        mediaFile.getObjectKey(),
                        finalUrl,
                        mediaBackup.payload().length,
                        document.getCreatedAt()
                );
                return new ResolvedKycDocument(document, mediaFile, "DATABASE_BACKUP", finalUrl);
            }
            log.warn(
                    "kyc media candidate purged accountHolderId={} documentSlot={} documentId={} mediaId={} source={} bucket={} objectKey={} finalUrl={} reason=LEGACY_FALLBACK_NOT_FOUND",
                    accountHolderId,
                    documentType,
                    document.getId(),
                    mediaId,
                    source,
                    mediaFile.getBucket(),
                    mediaFile.getObjectKey(),
                    finalUrl
            );
            markDocumentRejected(document, documentsToUpdate);
            markMediaDeleted(mediaFile, mediaFilesToUpdate);
            return null;
        }

        log.info(
                "kyc media resolved accountHolderId={} documentSlot={} documentId={} mediaId={} source={} bucket={} objectKey={} finalUrl={} documentCreatedAt={}",
                accountHolderId,
                documentType,
                document.getId(),
                mediaId,
                source,
                mediaFile.getBucket(),
                mediaFile.getObjectKey(),
                finalUrl,
                document.getCreatedAt()
        );
        return new ResolvedKycDocument(document, mediaFile, source, finalUrl);
    }

    private java.util.Optional<KycMediaBackupService.StoredKycMediaBackup> findKycBackup(MediaFile mediaFile) {
        java.util.Optional<KycMediaBackupService.StoredKycMediaBackup> backup = kycMediaBackupService.findBackup(mediaFile);
        return backup == null ? java.util.Optional.empty() : backup;
    }

    private boolean isAcceptableForResolution(KycDocument document) {
        return document.getStatus() == KycDocumentStatus.SUBMITTED
                || document.getStatus() == KycDocumentStatus.VERIFIED;
    }

    private void markDocumentRejected(KycDocument document, List<KycDocument> documentsToUpdate) {
        if (document.getStatus() == KycDocumentStatus.REJECTED) {
            return;
        }
        document.setStatus(KycDocumentStatus.REJECTED);
        documentsToUpdate.add(document);
    }

    private void markMediaDeleted(MediaFile mediaFile, List<MediaFile> mediaFilesToUpdate) {
        if (mediaFile.getStatus() == MediaStatus.DELETED) {
            return;
        }
        mediaFile.setStatus(MediaStatus.DELETED);
        mediaFilesToUpdate.add(mediaFile);
    }

    private boolean isLegacyFallbackUrl(String finalUrl) {
        return finalUrl != null && finalUrl.contains("/dev-media/");
    }

    private MediaType expectedMediaType(KycDocumentType documentType) {
        return switch (documentType) {
            case IDENTITY_FRONT -> MediaType.KYC_IDENTITY_FRONT;
            case IDENTITY_BACK -> MediaType.KYC_IDENTITY_BACK;
            case SELFIE -> MediaType.KYC_SELFIE;
        };
    }

    public record ResolvedKycDocument(
            KycDocument document,
            MediaFile mediaFile,
            String source,
            String finalUrl
    ) {
    }
}
