package com.ccapp.media.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.cpass.kyc.CPassKycDocumentResponse;
import com.ccapp.cpass.kyc.CPassKycGateway;
import com.ccapp.cpass.kyc.CPassKycInitResponse;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.media.domain.dto.SubmitKycResponse;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.repository.KycDocumentRepository;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.media.storage.PresignedUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private KycDocumentRepository kycDocumentRepository;
    @Mock
    private MediaFileRepository mediaFileRepository;
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
    private ObjectStorageService objectStorageService;
    @Mock
    private CPassKycGateway cPassKycGateway;

    private KycSubmissionService kycSubmissionService;

    @BeforeEach
    void setUp() {
        kycSubmissionService = new KycSubmissionService(
                kycDocumentRepository,
                mediaFileRepository,
                accountHolderRepository,
                accessControlService,
                countryContextService,
                delegatedActionService,
                kycReviewService,
                objectStorageService
        );
        ReflectionTestUtils.setField(kycSubmissionService, "cPassKycGateway", cPassKycGateway);
        ReflectionTestUtils.setField(kycSubmissionService, "requestedKycLevel", "LEVEL_1");
        ReflectionTestUtils.setField(kycSubmissionService, "downloadUrlTtlMinutes", 15L);
    }

    @Test
    void submit_shouldMarkPendingOnlyAfterFullDossierIsSentToCpass() {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);
        List<KycDocument> documents = buildMinimalKycDocuments();

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentRepository.findByAccountHolderId(ACCOUNT_HOLDER_ID)).thenReturn(documents);
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(mediaFileRepository.findByIdAndAccountHolderId(any(UUID.class), eq(ACCOUNT_HOLDER_ID)))
                .thenAnswer(invocation -> Optional.of(buildReadyMediaFile(
                        invocation.getArgument(0, UUID.class),
                        ACCOUNT_HOLDER_ID
                )));
        when(objectStorageService.createDownloadUrl(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Path tempFile = Files.createTempFile("kyc-upload-", ".jpg");
                    Files.writeString(tempFile, "fake-image-content");
                    return new PresignedUrl(tempFile.toUri().toString(), OffsetDateTime.now().plusMinutes(15));
                });
        when(cPassKycGateway.checkOrInitKyc(any()))
                .thenReturn(new CPassKycInitResponse("PENDING", "LEVEL_1", "case-123", "ref-123"));
        when(cPassKycGateway.submitDocument(any(), any(), any(), any()))
                .thenReturn(new CPassKycDocumentResponse("doc-123", "SUBMITTED", "object-key", "PENDING"));
        doAnswer(invocation -> {
            accountHolder.setKycStatus(KycStatus.PENDING);
            return null;
        }).when(kycReviewService).markPending(ACCOUNT_HOLDER_ID);

        SubmitKycResponse response = kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertEquals(KycStatus.PENDING, response.kycStatus());
        verify(accessControlService).assertCanActOnAccountHolder(ACTOR_USER_ID, ACCOUNT_HOLDER_ID, PermissionLevel.FULL);
        verify(cPassKycGateway).checkOrInitKyc(any());
        verify(cPassKycGateway, times(3)).submitDocument(eq("case-123"), any(), any(), any());
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
        List<KycDocument> incompleteDocuments = buildMinimalKycDocuments().subList(0, 2);

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentRepository.findByAccountHolderId(ACCOUNT_HOLDER_ID)).thenReturn(incompleteDocuments);
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID)
        );

        assertEquals("MINIMAL_KYC_INCOMPLETE", exception.getMessage());
        verify(cPassKycGateway, never()).checkOrInitKyc(any());
        verify(kycReviewService, never()).markPending(any());
        verify(delegatedActionService, never()).logIfDelegatedActor(any(), any(), any(), any());
    }

    @Test
    void submit_shouldFailWhenCpassIsUnavailableAndLeaveStatusUntouched() {
        AccountHolder accountHolder = buildAccountHolder(KycStatus.NOT_STARTED);
        List<KycDocument> documents = buildMinimalKycDocuments();

        ReflectionTestUtils.setField(kycSubmissionService, "cPassKycGateway", null);
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(kycDocumentRepository.findByAccountHolderId(ACCOUNT_HOLDER_ID)).thenReturn(documents);
        when(countryContextService.resolve(ACTOR_USER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(countryContextService.resolve(ACCOUNT_HOLDER_ID)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> kycSubmissionService.submit(ACTOR_USER_ID, ACTOR_USER_ID, ACCOUNT_HOLDER_ID)
        );

        assertEquals("CPASS_KYC_UNAVAILABLE", exception.getMessage());
        assertEquals(KycStatus.NOT_STARTED, accountHolder.getKycStatus());
        verify(kycReviewService, never()).markPending(any());
        verify(delegatedActionService, never()).logIfDelegatedActor(any(), any(), any(), any());
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
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private List<KycDocument> buildMinimalKycDocuments() {
        return List.of(
                buildDocument(KycDocumentType.IDENTITY_FRONT),
                buildDocument(KycDocumentType.IDENTITY_BACK),
                buildDocument(KycDocumentType.SELFIE)
        );
    }

    private KycDocument buildDocument(KycDocumentType documentType) {
        return KycDocument.builder()
                .id(UUID.randomUUID())
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .mediaFileId(UUID.randomUUID())
                .documentType(documentType)
                .status(KycDocumentStatus.SUBMITTED)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private MediaFile buildReadyMediaFile(UUID mediaFileId, UUID accountHolderId) {
        return MediaFile.builder()
                .id(mediaFileId)
                .accountHolderId(accountHolderId)
                .type(com.ccapp.media.domain.enums.MediaType.KYC_IDENTITY_FRONT)
                .bucket("cc-media-kyc")
                .objectKey("account-holders/" + accountHolderId + "/" + mediaFileId + ".jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .status(MediaStatus.READY)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
