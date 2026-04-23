package com.ccapp.media.controller;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.managedaccounts.service.ManagedAccountReadContextService;
import com.ccapp.identity.service.CurrentIdentityService;
import com.ccapp.media.domain.dto.DeclareIdentityRequest;
import com.ccapp.media.domain.dto.KycDraftResponse;
import com.ccapp.media.service.KycDraftService;
import com.ccapp.media.service.KycSubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileKycControllerIdentityResolutionTest {

    @Mock
    private KycSubmissionService kycSubmissionService;
    @Mock
    private KycDraftService kycDraftService;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private ManagedAccountReadContextService managedAccountReadContextService;
    @Mock
    private CurrentIdentityService currentIdentityService;

    @Test
    void declareIdentity_shouldUseCanonicalAppUserAndAccountHolderFromFirebaseToken() {
        UUID staleRequestedAccountHolderId = null;
        UUID canonicalUserId = UUID.randomUUID();
        UUID canonicalAccountHolderId = UUID.randomUUID();
        AppUser actor = appUser(canonicalUserId, canonicalAccountHolderId);
        MobileKycController controller = new MobileKycController(
                kycSubmissionService,
                kycDraftService,
                accountHolderService,
                managedAccountReadContextService,
                currentIdentityService
        );
        DeclareIdentityRequest request = new DeclareIdentityRequest(
                staleRequestedAccountHolderId,
                "Jean",
                "Test",
                LocalDate.of(1990, 1, 1),
                "BJ",
                "CNI",
                "ABC123"
        );

        when(currentIdentityService.resolveCurrentAppUser()).thenReturn(actor);
        when(managedAccountReadContextService.resolveViewTargetAccountHolderId(
                canonicalUserId,
                canonicalAccountHolderId,
                null
        )).thenReturn(canonicalAccountHolderId);

        controller.declareIdentity(request);

        verify(accountHolderService).declareIdentity(
                canonicalAccountHolderId,
                "Jean",
                "Test",
                LocalDate.of(1990, 1, 1),
                "BJ",
                "CNI",
                "ABC123"
        );
    }

    @Test
    void getDraft_shouldReadDraftForCanonicalAccountHolder() {
        UUID canonicalUserId = UUID.randomUUID();
        UUID canonicalAccountHolderId = UUID.randomUUID();
        AppUser actor = appUser(canonicalUserId, canonicalAccountHolderId);
        KycDraftResponse draft = new KycDraftResponse(
                canonicalAccountHolderId,
                "Jean",
                "Test",
                null,
                null,
                null,
                null,
                Map.of(),
                KycStatus.NOT_STARTED,
                KycLevel.LEVEL_0_UNVERIFIED,
                false,
                null
        );
        MobileKycController controller = new MobileKycController(
                kycSubmissionService,
                kycDraftService,
                accountHolderService,
                managedAccountReadContextService,
                currentIdentityService
        );

        when(currentIdentityService.resolveCurrentAppUser()).thenReturn(actor);
        when(managedAccountReadContextService.resolveViewTargetAccountHolderId(
                canonicalUserId,
                canonicalAccountHolderId,
                null
        )).thenReturn(canonicalAccountHolderId);
        when(kycDraftService.getDraft(canonicalAccountHolderId)).thenReturn(draft);

        var response = controller.getDraft(null);

        assertThat(response.getBody().data().accountHolderId()).isEqualTo(canonicalAccountHolderId);
        verify(kycDraftService).getDraft(canonicalAccountHolderId);
    }

    private AppUser appUser(UUID userId, UUID accountHolderId) {
        return AppUser.builder()
                .id(userId)
                .firebaseUid("firebase-uid")
                .accountHolderId(accountHolderId)
                .role(UserRole.USER)
                .build();
    }
}
