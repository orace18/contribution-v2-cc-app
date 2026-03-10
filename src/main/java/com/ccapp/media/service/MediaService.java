package com.ccapp.media.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
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
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.media.storage.PresignedUrl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaFileRepository mediaFileRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final AccountHolderService accountHolderService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final ObjectStorageService objectStorageService;

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
    public CreateUploadUrlResponse createUploadUrl(UUID accountHolderId, CreateUploadUrlRequest request) {
        accountHolderService.getById(accountHolderId);
        validateRequest(request);

        String bucket = resolveBucket(request.type());
        String objectKey = buildObjectKey(accountHolderId, request.type(), request.originalFileName());

        MediaFile media = mediaFileRepository.save(MediaFile.builder()
                .accountHolderId(accountHolderId)
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

        return new CreateUploadUrlResponse(
                media.getId(),
                bucket,
                objectKey,
                uploadUrl.url(),
                uploadUrl.expiresAt()
        );
    }

    @Transactional
    public MediaFileResponse confirmUpload(UUID accountHolderId, ConfirmUploadRequest request) {
        MediaFile media = loadOwnedMedia(accountHolderId, request.mediaId());
        if (media.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new BusinessException("Media file is not pending upload confirmation");
        }
        if (!objectStorageService.objectExists(media.getBucket(), media.getObjectKey())) {
            throw new BusinessException("Uploaded media not found in object storage");
        }

        media.setStatus(MediaStatus.READY);
        MediaFile saved = mediaFileRepository.save(media);
        attachMediaToDomain(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MediaFileResponse getMedia(UUID accountHolderId, UUID mediaId) {
        return toResponse(loadOwnedMedia(accountHolderId, mediaId));
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse createDownloadUrl(UUID accountHolderId, UUID mediaId) {
        MediaFile media = loadOwnedMedia(accountHolderId, mediaId);
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
            case PROFILE_PHOTO, GENERIC_PUBLIC -> publicBucket;
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
            KycDocument document = kycDocumentRepository.findByAccountHolderIdAndMediaFileId(media.getAccountHolderId(), media.getId())
                    .orElseGet(() -> KycDocument.builder()
                            .accountHolderId(media.getAccountHolderId())
                            .mediaFileId(media.getId())
                            .documentType(kycDocumentType)
                            .status(KycDocumentStatus.SUBMITTED)
                            .build());
            document.setDocumentType(kycDocumentType);
            kycDocumentRepository.save(document);
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
            case GENERIC_PRIVATE -> false;
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

    private MediaFileResponse toResponse(MediaFile media) {
        return new MediaFileResponse(
                media.getId(),
                media.getAccountHolderId(),
                media.getType(),
                media.getBucket(),
                media.getObjectKey(),
                media.getContentType(),
                media.getSize(),
                media.getStatus(),
                media.getCreatedAt()
        );
    }
}
