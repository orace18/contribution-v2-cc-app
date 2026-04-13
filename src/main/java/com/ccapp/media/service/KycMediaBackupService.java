package com.ccapp.media.service;

import com.ccapp.media.domain.entity.KycMediaBackup;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.repository.KycMediaBackupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycMediaBackupService {

    private final KycMediaBackupRepository kycMediaBackupRepository;

    @Transactional
    public void store(MediaFile mediaFile, byte[] payload, String origin) {
        if (mediaFile == null || payload == null || payload.length == 0 || !isKycMediaType(mediaFile.getType())) {
            return;
        }
        KycMediaBackup backup = KycMediaBackup.builder()
                .mediaFileId(mediaFile.getId())
                .accountHolderId(mediaFile.getAccountHolderId())
                .contentType(mediaFile.getContentType())
                .payload(payload)
                .build();
        kycMediaBackupRepository.save(backup);
        log.info(
                "kyc media backup stored accountHolderId={} mediaId={} mediaType={} bytes={} origin={}",
                mediaFile.getAccountHolderId(),
                mediaFile.getId(),
                mediaFile.getType(),
                payload.length,
                origin
        );
    }

    @Transactional(readOnly = true)
    public Optional<StoredKycMediaBackup> findBackup(MediaFile mediaFile) {
        if (mediaFile == null || !isKycMediaType(mediaFile.getType())) {
            return Optional.empty();
        }
        return kycMediaBackupRepository
                .findByMediaFileIdAndAccountHolderId(mediaFile.getId(), mediaFile.getAccountHolderId())
                .map(backup -> new StoredKycMediaBackup(
                        backup.getMediaFileId(),
                        backup.getAccountHolderId(),
                        backup.getContentType(),
                        backup.getPayload()
                ));
    }

    private boolean isKycMediaType(MediaType mediaType) {
        return mediaType == MediaType.KYC_IDENTITY_FRONT
                || mediaType == MediaType.KYC_IDENTITY_BACK
                || mediaType == MediaType.KYC_SELFIE;
    }

    public record StoredKycMediaBackup(
            UUID mediaFileId,
            UUID accountHolderId,
            String contentType,
            byte[] payload
    ) {
    }
}
