package com.ccapp.media.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.media.domain.entity.KycDocument;
import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;
import com.ccapp.media.repository.KycDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycReviewServiceTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();

    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KycDocumentRepository kycDocumentRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private KycNotificationService kycNotificationService;

    @InjectMocks
    private KycReviewService service;

    @Test
    void approveKyc_shouldNotifyVerified() {
        AccountHolder accountHolder = accountHolder();
        User user = user();
        List<KycDocument> documents = List.of(submittedDocument());

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(userRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(user));
        when(kycDocumentRepository.findByAccountHolderId(ACCOUNT_HOLDER_ID)).thenReturn(documents);

        service.approveKyc(ACCOUNT_HOLDER_ID, KycLevel.LEVEL_1_BASIC);

        assertEquals(KycStatus.VERIFIED, accountHolder.getKycStatus());
        assertEquals(KycLevel.LEVEL_1_BASIC, accountHolder.getKycLevel());
        assertEquals(KycStatus.VERIFIED, user.getKycStatus());
        assertEquals(KycDocumentStatus.VERIFIED, documents.get(0).getStatus());
        verify(domainEventPublisher).publish(any());
        verify(kycNotificationService).notifyVerified(ACCOUNT_HOLDER_ID);
    }

    @Test
    void rejectKyc_shouldNotifyRejected() {
        AccountHolder accountHolder = accountHolder();
        User user = user();
        List<KycDocument> documents = List.of(submittedDocument());

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(userRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(user));
        when(kycDocumentRepository.findByAccountHolderId(ACCOUNT_HOLDER_ID)).thenReturn(documents);

        service.rejectKyc(ACCOUNT_HOLDER_ID);

        assertEquals(KycStatus.REJECTED, accountHolder.getKycStatus());
        assertNull(accountHolder.getKycVerifiedAt());
        assertEquals(KycStatus.REJECTED, user.getKycStatus());
        assertEquals(KycDocumentStatus.REJECTED, documents.get(0).getStatus());
        verify(kycNotificationService).notifyRejected(ACCOUNT_HOLDER_ID);
    }

    @Test
    void requestResubmission_shouldNotifyResubmissionRequired() {
        AccountHolder accountHolder = accountHolder();
        User user = user();
        List<KycDocument> documents = List.of(submittedDocument());

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(userRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(user));
        when(kycDocumentRepository.findByAccountHolderId(ACCOUNT_HOLDER_ID)).thenReturn(documents);

        service.requestResubmission(ACCOUNT_HOLDER_ID);

        assertEquals(KycStatus.RESUBMIT_REQUIRED, accountHolder.getKycStatus());
        assertNull(accountHolder.getKycVerifiedAt());
        assertEquals(KycStatus.RESUBMIT_REQUIRED, user.getKycStatus());
        assertEquals(KycDocumentStatus.REJECTED, documents.get(0).getStatus());
        verify(kycNotificationService).notifyResubmissionRequired(ACCOUNT_HOLDER_ID);
    }

    private AccountHolder accountHolder() {
        return AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .firstName("John")
                .lastName("Doe")
                .kycStatus(KycStatus.PENDING)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .kycVerifiedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private User user() {
        return User.builder()
                .id(ACCOUNT_HOLDER_ID)
                .firebaseUid("firebase-uid")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();
    }

    private KycDocument submittedDocument() {
        return KycDocument.builder()
                .id(UUID.randomUUID())
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .mediaFileId(UUID.randomUUID())
                .documentType(KycDocumentType.IDENTITY_FRONT)
                .status(KycDocumentStatus.SUBMITTED)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
