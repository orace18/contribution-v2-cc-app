package com.ccapp.identity.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.media.repository.KycDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycStatusConsistencyServiceTest {

    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @InjectMocks
    private KycStatusConsistencyService kycStatusConsistencyService;

    @Test
    void reconcileAccountHolderStatus_shouldResetStalePendingWhenMinimalKycDossierIsIncomplete() {
        UUID accountHolderId = UUID.randomUUID();
        AccountHolder accountHolder = AccountHolder.builder()
                .id(accountHolderId)
                .firstName("John")
                .lastName("Doe")
                .kycStatus(KycStatus.PENDING)
                .build();
        User user = User.builder()
                .id(accountHolderId)
                .firebaseUid("firebase-" + accountHolderId)
                .email("john@example.com")
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();

        when(accountHolderRepository.findById(accountHolderId)).thenReturn(Optional.of(accountHolder));
        when(userRepository.findById(accountHolderId)).thenReturn(Optional.of(user));
        when(kycDocumentRepository.findByAccountHolderId(accountHolderId)).thenReturn(List.of());

        KycStatus resolvedStatus = kycStatusConsistencyService.reconcileAccountHolderStatus(accountHolderId);

        assertEquals(KycStatus.NOT_STARTED, resolvedStatus);
        assertEquals(KycStatus.NOT_STARTED, accountHolder.getKycStatus());
        assertEquals(KycStatus.NOT_STARTED, user.getKycStatus());
        verify(accountHolderRepository).save(accountHolder);
        verify(userRepository).save(user);
    }
}
