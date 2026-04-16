package com.ccapp.media.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.media.domain.dto.*;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.domain.enums.MediaType;
import com.ccapp.media.repository.KycDocumentRepository;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.LocalFileStorageService;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.media.storage.PresignedUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private static final String KYC_PROXY_UPLOAD_BASE_URL = "https://kyc-upload.internal";

    private final MediaFileRepository mediaFileRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final CountryContextService countryContextService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final ObjectStorageService objectStorageService;
    private final KycMediaBackupService kycMediaBackupService;

    @Value("${media.bucket.public:cc-media-public}")
    private String publicBucket;

    @Value("${media.bucket.private:cc-media-private}")
    private String privateBucket;

    @Value("${media.bucket.kyc:cc-media-kyc}")
    private String kycBucket;

    @Value("${media.upload-url-ttl-minutes:15}")
    private long uploadUrlTtlMinutes;

    @Value("${media.download-url-ttl-minutes:15}")
    private long downloadUrlTtlMinutes;

    @Value("${media.max-size-bytes:10485760}")
    private long maxSizeBytes;

    @Transactional
    public CreateUploadUrlResponse createUploadUrl(UUID actorUserId,
                                                   UUID actorAccountHolderId,
                                                   CreateUploadUrlRequest request) {
        UUID targetAccountHolderId = resolveTargetAccountHolderId(actorAccountHolderId, request.accountHolderId());
        assertDelegatedAccess(actorUserId, actorAccountHolderId, targetAccountHolderId);
        accountHolderService.getById(targetAccountHolderId);
        validateRequest(request);

        String bucket = resolveBucket(request.type());
        String objectKey = buildObjectKey(targetAccountHolderId, request.type(), request.originalFileName());

        MediaFile media = mediaFileRepository.save(MediaFile.builder()
                .accountHolderId(targetAccountHolderId)
                .type(request.type())
                .bucket(bucket)
                .objectKey(objectKey)
                .contentType(request.contentType().trim().toLowerCase(Locale.ROOT))
                .size(request.size())
                .status(MediaStatus.PENDING_UPLOAD)
                .build());

        PresignedUrl uploadUrl = objectStorageService.createUploadUrl(
                bucket,
                objectKey,
                media.getContentType(),
                Duration.ofMinutes(uploadUrlTtlMinutes)
        );

        String clientUploadUrl = resolveClientUploadUrl(request.type(), media.getId(), uploadUrl.url());

        return new CreateUploadUrlResponse(
                media.getId(),
                bucket,
                objectKey,
                clientUploadUrl,
                uploadUrl.expiresAt()
        );
    }

    @Transactional
    public MediaFileResponse confirmUpload(UUID actorUserId,
                                           UUID actorAccountHolderId,
                                           ConfirmUploadRequest request) {
        UUID targetAccountHolderId = resolveTargetAccountHolderId(actorAccountHolderId, request.accountHolderId());
        assertDelegatedAccess(actorUserId, actorAccountHolderId, targetAccountHolderId);
        MediaFile media = loadOwnedMedia(targetAccountHolderId, request.mediaId());
        if (media.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new BusinessException("Media file is not pending upload confirmation");
        }
        if (!objectStorageService.objectExists(media.getBucket(), media.getObjectKey())) {
            throw new BusinessException("Uploaded media not found in object storage");
        }

        media.setStatus(MediaStatus.READY);
        MediaFile saved = mediaFileRepository.save(media);
        persistKycBackupIfNeeded(saved, readStoredBytesSafely(saved), "CONFIRM_UPLOAD");
        attachMediaToDomain(saved);
        return toResponse(saved);
    }

    @Transactional
    public MediaFileResponse proxyUpload(UUID actorUserId,
                                         UUID actorAccountHolderId,
                                         UUID mediaId,
                                         byte[] data,
                                         String contentType) {
        MediaFile media = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("Media file not found"));
        assertDelegatedAccess(actorUserId, actorAccountHolderId, media.getAccountHolderId());
        if (media.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new BusinessException("Media file is not pending upload");
        }
        objectStorageService.putObject(media.getBucket(), media.getObjectKey(), data, media.getContentType());
        media.setStatus(MediaStatus.READY);
        MediaFile saved = mediaFileRepository.save(media);
        persistKycBackupIfNeeded(saved, data, "UPLOAD_PROXY");
        attachMediaToDomain(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MediaFileResponse getMedia(UUID actorUserId,
                                      UUID actorAccountHolderId,
                                      UUID mediaId) {
        MediaFile media = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("Media file not found"));
        assertDelegatedAccess(actorUserId, actorAccountHolderId, media.getAccountHolderId());
        return toResponse(media);
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse createDownloadUrl(UUID actorUserId,
                                                 UUID actorAccountHolderId,
                                                 UUID mediaId) {
        MediaFile media = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("Media file not found"));
        assertDelegatedAccess(actorUserId, actorAccountHolderId, media.getAccountHolderId());
        if (media.getStatus() != MediaStatus.READY) {
            throw new BusinessException("Media file is not available");
        }

        PresignedUrl downloadUrl = objectStorageService.createDownloadUrl(
                media.getBucket(),
                media.getObjectKey(),
                Duration.ofMinutes(downloadUrlTtlMinutes)
        );
        return new DownloadUrlResponse(media.getId(), downloadUrl.url(), downloadUrl.expiresAt());
    }

    private void validateRequest(CreateUploadUrlRequest request) {
        if (request.size() > maxSizeBytes) {
            throw new BusinessException("Media size exceeds configured limit");
        }
        if (isImageType(request.type()) && !request.contentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("Image media type requires image/* contentType");
        }
    }

    private String resolveBucket(MediaType type) {
        return switch (type) {
            case PROFILE_PHOTO, GENERIC_PUBLIC, PRAYER_POST, CIRCLE_CHAT -> publicBucket;
            case KYC_IDENTITY_FRONT, KYC_IDENTITY_BACK, KYC_SELFIE -> kycBucket;
            case GENERIC_PRIVATE -> privateBucket;
        };
    }

    private String buildObjectKey(UUID accountHolderId, MediaType type, String originalFileName) {
        String sanitizedName = sanitizeFileName(originalFileName);
        return "account-holders/" + accountHolderId + "/" + type.name().toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID() + "-" + sanitizedName;
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "file";
        }
        String normalized = originalFileName.trim().replace("\\", "_").replace("/", "_");
        StringBuilder builder = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        if (builder.isEmpty()) {
            return "file";
        }
        return builder.toString();
    }

    private void attachMediaToDomain(MediaFile media) {
        if (media.getType() == MediaType.PROFILE_PHOTO) {
            UserProfile profile = userProfileRepository.findByUserId(media.getAccountHolderId())
                    .orElseGet(() -> createProfileSkeleton(media.getAccountHolderId()));
            profile.setProfilePhotoMediaId(media.getId());
            userProfileRepository.save(profile);
            return;
        }

        KycDocumentType kycDocumentType = toKycDocumentType(media.getType());
        if (kycDocumentType != null) {
            List<KycDocument> accountDocuments = kycDocumentRepository.findByAccountHolderId(media.getAccountHolderId());

            List<KycDocument> supersededDocuments = accountDocuments.stream()
                    .filter(document -> document.getDocumentType() == kycDocumentType)
                    .filter(document -> !document.getMediaFileId().equals(media.getId()))
                    .filter(document -> document.getStatus() == KycDocumentStatus.SUBMITTED
                            || document.getStatus() == KycDocumentStatus.VERIFIED)
                    .toList();
            for (KycDocument supersededDocument : supersededDocuments) {
                supersededDocument.setStatus(KycDocumentStatus.REJECTED);
            }
            if (!supersededDocuments.isEmpty()) {
                kycDocumentRepository.saveAll(supersededDocuments);
                log.info(
                        "kyc media superseded accountHolderId={} documentSlot={} newMediaId={} replacedMediaIds={}",
                        media.getAccountHolderId(),
                        kycDocumentType,
                        media.getId(),
                        supersededDocuments.stream()
                                .map(KycDocument::getMediaFileId)
                                .map(UUID::toString)
                                .collect(Collectors.toList())
                );
            }

            KycDocument document = accountDocuments.stream()
                    .filter(existing -> existing.getMediaFileId().equals(media.getId()))
                    .findFirst()
                    .orElseGet(() -> KycDocument.builder()
                            .accountHolderId(media.getAccountHolderId())
                            .mediaFileId(media.getId())
                            .documentType(kycDocumentType)
                            .status(KycDocumentStatus.SUBMITTED)
                            .build());
            document.setDocumentType(kycDocumentType);
            kycDocumentRepository.save(document);
            log.info(
                    "kyc media attached accountHolderId={} documentSlot={} mediaId={} bucket={} objectKey={} finalUrl={}",
                    media.getAccountHolderId(),
                    kycDocumentType,
                    media.getId(),
                    media.getBucket(),
                    media.getObjectKey(),
                    objectStorageService.publicObjectUrl(media.getBucket(), media.getObjectKey())
            );
        }
    }

    private KycDocumentType toKycDocumentType(MediaType mediaType) {
        return switch (mediaType) {
            case KYC_IDENTITY_FRONT -> KycDocumentType.IDENTITY_FRONT;
            case KYC_IDENTITY_BACK -> KycDocumentType.IDENTITY_BACK;
            case KYC_SELFIE -> KycDocumentType.SELFIE;
            default -> null;
        };
    }

    private boolean isImageType(MediaType type) {
        return switch (type) {
            case PROFILE_PHOTO, KYC_IDENTITY_FRONT, KYC_IDENTITY_BACK, KYC_SELFIE, GENERIC_PUBLIC -> true;
            case GENERIC_PRIVATE, PRAYER_POST, CIRCLE_CHAT -> false; // prayer posts and circle chat can carry non-image media
        };
    }

    private UserProfile createProfileSkeleton(UUID accountHolderId) {
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        User user = userRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return UserProfile.builder()
                .user(user)
                .firstName(accountHolder.getFirstName())
                .lastName(accountHolder.getLastName())
                .country(accountHolder.getCountryCode() != null ? accountHolder.getCountryCode() : "INT")
                .phone(accountHolder.getPhoneNumber() != null ? accountHolder.getPhoneNumber() : "")
                .onboardingStep(OnboardingStep.STEP_1)
                .onboardingCompleted(false)
                .build();
    }

    private MediaFile loadOwnedMedia(UUID accountHolderId, UUID mediaId) {
        accountHolderService.getById(accountHolderId);
        return mediaFileRepository.findByIdAndAccountHolderId(mediaId, accountHolderId)
                .orElseThrow(() -> new NotFoundException("Media file not found"));
    }

    private UUID resolveTargetAccountHolderId(UUID actorAccountHolderId, UUID requestedTargetAccountHolderId) {
        return requestedTargetAccountHolderId != null ? requestedTargetAccountHolderId : actorAccountHolderId;
    }

    private void assertDelegatedAccess(UUID actorUserId, UUID actorAccountHolderId, UUID targetAccountHolderId) {
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }
        accessControlService.assertCanActOnAccountHolder(actorUserId, targetAccountHolderId, PermissionLevel.FULL);

        CountryContext actorContext = countryContextService.resolve(actorAccountHolderId);
        CountryContext targetContext = countryContextService.resolve(targetAccountHolderId);
        if (!actorContext.serviceCountryCode().equalsIgnoreCase(targetContext.serviceCountryCode())) {
            throw new BusinessException("CROSS_COUNTRY_DELEGATION_NOT_ALLOWED");
        }
    }

    private MediaFileResponse toResponse(MediaFile media) {
        String publicUrl = objectStorageService.publicObjectUrl(media.getBucket(), media.getObjectKey());
        if (isLegacyFallbackUrl(publicUrl) && isKycMediaType(media.getType())) {
            log.info(
                    "kyc media public url suppressed accountHolderId={} mediaId={} bucket={} objectKey={} suppressedUrl={}",
                    media.getAccountHolderId(),
                    media.getId(),
                    media.getBucket(),
                    media.getObjectKey(),
                    publicUrl
            );
            publicUrl = null;
        }
        return new MediaFileResponse(
                media.getId(),
                media.getAccountHolderId(),
                media.getType(),
                media.getBucket(),
                media.getObjectKey(),
                publicUrl,
                media.getContentType(),
                media.getSize(),
                media.getStatus(),
                media.getCreatedAt()
        );
    }

    private String resolveClientUploadUrl(MediaType mediaType, UUID mediaId, String storageUploadUrl) {
        if (!isKycMediaType(mediaType) || !isLegacyFallbackUrl(storageUploadUrl)) {
            return storageUploadUrl;
        }
        String proxyUploadUrl = KYC_PROXY_UPLOAD_BASE_URL + "/api/media/" + mediaId + "/upload-proxy";
        log.info(
                "kyc upload url remapped mediaId={} mediaType={} storageUploadUrl={} clientUploadUrl={} reason=LEGACY_DEV_MEDIA_FALLBACK",
                mediaId,
                mediaType,
                storageUploadUrl,
                proxyUploadUrl
        );
        return proxyUploadUrl;
    }

    private boolean isLegacyFallbackUrl(String url) {
        return url != null && url.contains("/dev-media/");
    }

    private boolean isKycMediaType(MediaType type) {
        return type == MediaType.KYC_IDENTITY_FRONT
                || type == MediaType.KYC_IDENTITY_BACK
                || type == MediaType.KYC_SELFIE;
    }

    private void persistKycBackupIfNeeded(MediaFile media, byte[] bytes, String origin) {
        if (!isKycMediaType(media.getType()) || bytes == null || bytes.length == 0 || !(objectStorageService instanceof LocalFileStorageService)) {
            return;
        }
        kycMediaBackupService.store(media, bytes, origin);
    }

    private byte[] readStoredBytesSafely(MediaFile media) {
        try {
            return objectStorageService.readObject(media.getBucket(), media.getObjectKey());
        } catch (RuntimeException ex) {
            log.warn(
                    "kyc media backup skipped accountHolderId={} mediaId={} bucket={} objectKey={} reason={}",
                    media.getAccountHolderId(),
                    media.getId(),
                    media.getBucket(),
                    media.getObjectKey(),
                    ex.getMessage()
            );
            return null;
        }
    }
}
