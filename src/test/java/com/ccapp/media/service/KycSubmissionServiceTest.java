package com.ccapp.media.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.cpass.kyc.CPassKycDocumentResponse;
import com.ccapp.cpass.kyc.CPassKycGateway;
import com.ccapp.cpass.kyc.CPassKycInitResponse;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.media.domain.dto.SubmitKycResponse;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.storage.ObjectStorageService;
import org.springframework.core.io.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycSubmissionServiceTest {

    private static final UUID ACTOR_USER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();

    @Mock
    private KycDocumentResolutionService kycDocumentResolutionService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private CountryContextService countryContextService;
    @Mock
    private DelegatedActionService delegatedActionService;
    @Mock
    private KycReviewService kycReviewService;
    @Mock
    private KycNotificationService kycNotificationService;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private FinancialAuditService financialAuditService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private CPassKycGateway cPassKycGateway;

    private KycSubmissionService kycSubmissionService;

    @BeforeEach
    void setUp() {
        kycSubmissionService = new KycSubmissionService(
                kycDocumentResolutionService,
                accountHolderRepository,
                accessControlService,
                countryContextService,
                delegatedActionService,
                kycReviewService,
                kycNotificationService,
                objectStorageService,
                financialAuditService,
                userProfileRepository
        );
        ReflectionTestUtils.setField(kycSubmissionService, "cPassKycGateway", cPassKycGateway);
        ReflectionTestUtils.setField(kycSubmissionService, "requestedKycLevel", "LEVEL_1");
        ReflectionTestUtils.setField(kycSubmissionService, "level1FaceMatchThreshold", 0.75d);
        ReflectionTestUtils.setField(kycSubmissionService, "cpassDocumentMaxBytes", 950_000);
        ReflectionTestUtils.setField(kycSubmissionService, "cpassImageMaxDimension", 1600);
        ReflectionTestUtils.setField(kycSubmissionService, "cpassImageJpegQuality", 0.82f);
    }

    @Test
    void submit_shouldMarkPendingOnlyAfterFullDossierIsSentToCpass() {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentResolutionService.resolveActiveDocuments(ACCOUNT_HOLDER_ID)).thenReturn(buildResolvedMinimalKycDocuments());
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(objectStorageService.readObject(any(), any()))
                .thenReturn("fake-image-content".getBytes());
        when(cPassKycGateway.checkOrInitKyc(any()))
                .thenReturn(new CPassKycInitResponse("PENDING", "LEVEL_1", "case-123", "ref-123", null, null, null, null, null, null, null, null));
        when(cPassKycGateway.submitDocument(any(), any(), any(), any()))
                .thenReturn(new CPassKycDocumentResponse("doc-123", "SUBMITTED", "object-key", "PENDING", null, null, null, null, null, null, null, null));
        doAnswer(invocation -> {
            accountHolder.setKycStatus(KycStatus.PENDING);
            return null;
        }).when(kycReviewService).markPending(ACCOUNT_HOLDER_ID);

        SubmitKycResponse response = kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertEquals(KycStatus.PENDING, response.kycStatus());
        assertEquals(KycLevel.LEVEL_0_UNVERIFIED, response.kycLevel());
        verify(accessControlService).assertCanActOnAccountHolder(ACTOR_USER_ID, ACCOUNT_HOLDER_ID, PermissionLevel.FULL);
        verify(cPassKycGateway).checkOrInitKyc(any());
        verify(cPassKycGateway, times(3)).submitDocument(eq("case-123"), any(), any(), any());
        verify(kycNotificationService).notifySubmitted(ACCOUNT_HOLDER_ID);
        verify(kycReviewService).markPending(ACCOUNT_HOLDER_ID);
        verify(delegatedActionService).logIfDelegatedActor(
                eq(ACCOUNT_HOLDER_ID),
                eq(ACTOR_USER_ID),
                eq(DelegatedActionType.KYC_SUBMITTED),
                any()
        );
    }

    @Test
    void submit_shouldFailWithoutCompleteDossierAndNeverReachCpass() {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentResolutionService.resolveActiveDocuments(ACCOUNT_HOLDER_ID))
                .thenReturn(buildResolvedMinimalKycDocuments(KycDocumentType.IDENTITY_FRONT, KycDocumentType.IDENTITY_BACK));
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID)
        );

        assertEquals("MINIMAL_KYC_INCOMPLETE", exception.getMessage());
        verify(cPassKycGateway, never()).checkOrInitKyc(any());
        verify(kycNotificationService, never()).notifySubmitted(any());
        verify(kycReviewService, never()).markPending(any());
        verify(delegatedActionService, never()).logIfDelegatedActor(any(), any(), any(), any());
    }

    @Test
    void submit_shouldFailWhenCpassIsUnavailableAndLeaveStatusUntouched() {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);

        ReflectionTestUtils.setField(kycSubmissionService, "cPassKycGateway", null);
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentResolutionService.resolveActiveDocuments(ACCOUNT_HOLDER_ID)).thenReturn(buildResolvedMinimalKycDocuments());
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID)
        );

        assertEquals("CPASS_KYC_UNAVAILABLE", exception.getMessage());
        assertEquals(KycStatus.NOT_STARTED, accountHolder.getKycStatus());
        verify(kycNotificationService, never()).notifySubmitted(any());
        verify(kycReviewService, never()).markPending(any());
        verify(delegatedActionService, never()).logIfDelegatedActor(any(), any(), any(), any());
    }

    @Test
    void submit_shouldAutoApproveLevel1WhenProviderSignalsAreVerifiedAndLowRisk() {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);
        UserProfile profile = UserProfile.builder().build();

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentResolutionService.resolveActiveDocuments(ACCOUNT_HOLDER_ID)).thenReturn(buildResolvedMinimalKycDocuments());
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(objectStorageService.readObject(any(), any()))
                .thenReturn("fake-image-content".getBytes());
        when(cPassKycGateway.checkOrInitKyc(any()))
                .thenReturn(new CPassKycInitResponse("PENDING", "LEVEL_1", "case-123", "ref-123", null, null, null, null, null, null, null, null));
        when(cPassKycGateway.submitDocument(any(), eq("IDENTITY_FRONT"), any(), any()))
                .thenReturn(new CPassKycDocumentResponse(
                        "doc-front",
                        "SUBMITTED",
                        "object-key-front",
                        "VALID",
                        true,
                        null,
                        "VERIFIED",
                        "LOW",
                        "Jane",
                        "Doe",
                        LocalDate.of(1990, 1, 2),
                        "DOC-12345"
                ));
        when(cPassKycGateway.submitDocument(any(), eq("IDENTITY_BACK"), any(), any()))
                .thenReturn(new CPassKycDocumentResponse(
                        "doc-back",
                        "SUBMITTED",
                        "object-key-back",
                        "VALID",
                        true,
                        null,
                        "VERIFIED",
                        "LOW",
                        "Jane",
                        "Doe",
                        LocalDate.of(1990, 1, 2),
                        "DOC-12345"
                ));
        when(cPassKycGateway.submitDocument(any(), eq("SELFIE"), any(), any()))
                .thenReturn(new CPassKycDocumentResponse(
                        "doc-selfie",
                        "SUBMITTED",
                        "object-key-selfie",
                        "VALID",
                        true,
                        0.93d,
                        "VERIFIED",
                        "LOW",
                        "Jane",
                        "Doe",
                        LocalDate.of(1990, 1, 2),
                        "DOC-12345"
                ));
        when(userProfileRepository.findByUserId(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(profile));
        doAnswer(invocation -> {
            accountHolder.setKycStatus(KycStatus.VERIFIED);
            accountHolder.setKycLevel(KycLevel.LEVEL_1_BASIC);
            return null;
        }).when(kycReviewService).approveKyc(ACCOUNT_HOLDER_ID, KycLevel.LEVEL_1_BASIC);

        SubmitKycResponse response = kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertEquals(KycStatus.VERIFIED, response.kycStatus());
        assertEquals(KycLevel.LEVEL_1_BASIC, response.kycLevel());
        assertEquals("Jane", accountHolder.getFirstName());
        assertEquals("Doe", accountHolder.getLastName());
        assertEquals(LocalDate.of(1990, 1, 2), accountHolder.getBirthDate());
        assertEquals("DOC-12345", accountHolder.getDocumentNumber());
        verify(kycNotificationService).notifySubmitted(ACCOUNT_HOLDER_ID);
        verify(kycReviewService).approveKyc(ACCOUNT_HOLDER_ID, KycLevel.LEVEL_1_BASIC);
        verify(kycReviewService, never()).markPending(ACCOUNT_HOLDER_ID);
        verify(userProfileRepository).save(profile);
        verify(financialAuditService).record(
                eq("KYC_LEVEL_1_AUTO_APPROVED"),
                eq("ref-123"),
                eq(ACCOUNT_HOLDER_ID),
                isNull(),
                any()
        );
    }

    @Test
    void submit_shouldFailWhenMediaReadFailsBeforeSendingDocumentsToCpass() {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentResolutionService.resolveActiveDocuments(ACCOUNT_HOLDER_ID)).thenReturn(buildResolvedMinimalKycDocuments());
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(objectStorageService.readObject(any(), any()))
                .thenThrow(new BusinessException("Local fallback media file not found"));
        when(cPassKycGateway.checkOrInitKyc(any()))
                .thenReturn(new CPassKycInitResponse("PENDING", "LEVEL_1", "case-123", "ref-123", null, null, null, null, null, null, null, null));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID)
        );

        assertEquals("CPASS_KYC_DOCUMENT_READ_FAILED", exception.getMessage());
        verify(cPassKycGateway).checkOrInitKyc(any());
        verify(cPassKycGateway, never()).submitDocument(any(), any(), any(), any());
        verify(kycNotificationService, never()).notifySubmitted(any());
    }

    @Test
    void submit_shouldCompressOversizedImagesBeforeSendingToCpass() throws IOException {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);
        byte[] oversizedBytes = createNoisyJpeg(1400, 1400, 0.98f);

        ReflectionTestUtils.setField(kycSubmissionService, "cpassDocumentMaxBytes", 180_000);
        ReflectionTestUtils.setField(kycSubmissionService, "cpassImageMaxDimension", 1200);
        ReflectionTestUtils.setField(kycSubmissionService, "cpassImageJpegQuality", 0.72f);

        assertTrue(oversizedBytes.length > 180_000);

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentResolutionService.resolveActiveDocuments(ACCOUNT_HOLDER_ID)).thenReturn(buildResolvedMinimalKycDocuments());
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(objectStorageService.readObject(any(), any())).thenReturn(oversizedBytes);
        when(cPassKycGateway.checkOrInitKyc(any()))
                .thenReturn(new CPassKycInitResponse("PENDING", "LEVEL_1", "case-123", "ref-123", null, null, null, null, null, null, null, null));
        when(cPassKycGateway.submitDocument(any(), any(), any(), any()))
                .thenReturn(new CPassKycDocumentResponse("doc-123", "SUBMITTED", "object-key", "PENDING", null, null, null, null, null, null, null, null));
        doAnswer(invocation -> {
            accountHolder.setKycStatus(KycStatus.PENDING);
            return null;
        }).when(kycReviewService).markPending(ACCOUNT_HOLDER_ID);

        SubmitKycResponse response = kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertEquals(KycStatus.PENDING, response.kycStatus());

        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(cPassKycGateway, times(3)).submitDocument(eq("case-123"), any(), resourceCaptor.capture(), any());
        for (Resource resource : resourceCaptor.getAllValues()) {
            assertTrue(resource.contentLength() <= 180_000);
            assertTrue(Objects.requireNonNull(resource.getFilename()).endsWith(".jpg"));
        }
    }

    private AccountHolder buildAccountHolder(KycStatus kycStatus) {
        return AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+229000000")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .kycStatus(kycStatus)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private Map<KycDocumentType, KycDocumentResolutionService.ResolvedKycDocument> buildResolvedMinimalKycDocuments(KycDocumentType... documentTypes) {
        Map<KycDocumentType, KycDocumentResolutionService.ResolvedKycDocument> resolvedDocuments =
                new java.util.EnumMap<>(KycDocumentType.class);
        List<KycDocumentType> types = documentTypes.length == 0
                ? List.of(KycDocumentType.IDENTITY_FRONT, KycDocumentType.IDENTITY_BACK, KycDocumentType.SELFIE)
                : List.of(documentTypes);
        for (KycDocumentType documentType : types) {
            KycDocument document = buildDocument(documentType);
            MediaFile mediaFile = buildReadyMediaFile(document.getMediaFileId(), ACCOUNT_HOLDER_ID, documentType);
            resolvedDocuments.put(
                    documentType,
                    new KycDocumentResolutionService.ResolvedKycDocument(
                            document,
                            mediaFile,
                            "OBJECT_STORAGE",
                            "https://media.test/" + mediaFile.getObjectKey()
                    )
            );
        }
        return resolvedDocuments;
    }

    private KycDocument buildDocument(KycDocumentType documentType) {
        return KycDocument.builder()
                .id(UUID.randomUUID())
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .mediaFileId(UUID.randomUUID())
                .documentType(documentType)
                .status(com.ccapp.media.domain.enums.KycDocumentStatus.SUBMITTED)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private MediaFile buildReadyMediaFile(UUID mediaFileId, UUID accountHolderId, KycDocumentType documentType) {
        return MediaFile.builder()
                .id(mediaFileId)
                .accountHolderId(accountHolderId)
                .type(toMediaType(documentType))
                .bucket("cc-media-kyc")
                .objectKey("account-holders/" + accountHolderId + "/" + documentType.name().toLowerCase() + "/" + mediaFileId + ".jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .status(MediaStatus.READY)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private com.ccapp.media.domain.enums.MediaType toMediaType(KycDocumentType documentType) {
        return switch (documentType) {
            case IDENTITY_FRONT -> com.ccapp.media.domain.enums.MediaType.KYC_IDENTITY_FRONT;
            case IDENTITY_BACK -> com.ccapp.media.domain.enums.MediaType.KYC_IDENTITY_BACK;
            case SELFIE -> com.ccapp.media.domain.enums.MediaType.KYC_SELFIE;
        };
    }

    private byte[] createNoisyJpeg(int width, int height, float quality) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = random.nextInt(256);
                int green = random.nextInt(256);
                int blue = random.nextInt(256);
                image.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        return encodeJpeg(image, quality);
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), writeParam);
            writer.dispose();
            return outputStream.toByteArray();
        }
    }
}
