package com.ccapp.identity.controller;

import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.managedaccounts.service.ManagedAccountReadContextService;
import com.ccapp.identity.service.CurrentIdentityService;
import com.ccapp.identity.service.KycStatusConsistencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycStatusControllerIdentityResolutionTest {

    @Mock
    private KycStatusConsistencyService kycStatusConsistencyService;
    @Mock
    private ManagedAccountReadContextService managedAccountReadContextService;
    @Mock
    private CurrentIdentityService currentIdentityService;

    @Test
    void getStatus_shouldUseCanonicalIdentityForActorAndDefaultAccountHolder() {
        UUID canonicalUserId = UUID.randomUUID();
        UUID canonicalAccountHolderId = UUID.randomUUID();
        KycStatusController controller = new KycStatusController(
                kycStatusConsistencyService,
                managedAccountReadContextService,
                currentIdentityService
        );
        AppUser actor = AppUser.builder()
                .id(canonicalUserId)
                .firebaseUid("firebase-uid")
                .accountHolderId(canonicalAccountHolderId)
                .role(UserRole.USER)
                .build();

        when(currentIdentityService.resolveCurrentAppUser()).thenReturn(actor);
        when(managedAccountReadContextService.resolveViewTargetAccountHolderId(
                canonicalUserId,
                canonicalAccountHolderId,
                null
        )).thenReturn(canonicalAccountHolderId);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(canonicalAccountHolderId))
                .thenReturn(KycStatus.NOT_STARTED);
        when(kycStatusConsistencyService.resolveAccountHolderLevel(canonicalAccountHolderId))
                .thenReturn(KycLevel.LEVEL_0_UNVERIFIED);

        var response = controller.getStatus(null, null);

        assertThat(response.getBody().data().kycStatus()).isEqualTo(KycStatus.NOT_STARTED);
        verify(managedAccountReadContextService).resolveViewTargetAccountHolderId(
                canonicalUserId,
                canonicalAccountHolderId,
                null
        );
    }
}
