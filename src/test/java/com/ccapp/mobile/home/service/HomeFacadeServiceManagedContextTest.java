package com.ccapp.mobile.home.service;

import com.ccapp.circles.service.CircleService;
import com.ccapp.circlesocial.service.CircleActivityService;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.service.CreditScoringService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.mobile.home.dto.MobileHomeDashboardResponse;
import com.ccapp.mobile.onboarding.repository.MobilePlanSelectionRepository;
import com.ccapp.payments.subscription.service.MembershipReconciliationService;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.prayer.service.PrayerSuggestionService;
import com.ccapp.progress.repository.UserTaskRepository;
import com.ccapp.progress.service.TaskEngineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeFacadeServiceManagedContextTest {

    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private TaskEngineService taskEngineService;
    @Mock
    private UserTaskRepository userTaskRepository;
    @Mock
    private MembershipService membershipService;
    @Mock
    private CircleService circleService;
    @Mock
    private CircleActivityService circleActivityService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private CreditScoringService creditScoringService;
    @Mock
    private MobilePlanSelectionRepository mobilePlanSelectionRepository;
    @Mock
    private PrayerSuggestionService prayerSuggestionService;
    @Mock
    private MembershipReconciliationService membershipReconciliationService;

    @InjectMocks
    private HomeFacadeService homeFacadeService;

    @Test
    void getHome_shouldExposeManagedAccountIndicatorsForDelegatedReadContext() {
        UUID actorUserId = UUID.randomUUID();
        UUID managerPrimaryAccountHolderId = UUID.randomUUID();
        UUID managedAccountHolderId = UUID.randomUUID();

        AccountHolder managedAccount = AccountHolder.builder()
                .id(managedAccountHolderId)
                .firstName("Awa")
                .lastName("Doe")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.ASSISTED)
                .createdVia(AccountCreatedVia.MANAGED_ACCOUNT)
                .kycStatus(KycStatus.NOT_STARTED)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .createdAt(OffsetDateTime.now().minusDays(4))
                .build();

        when(accountHolderService.getById(managedAccountHolderId)).thenReturn(managedAccount);
        when(membershipService.findLatestMembership(managedAccountHolderId)).thenReturn(Optional.empty());
        when(mobilePlanSelectionRepository.findByAccountHolderId(managedAccountHolderId)).thenReturn(Optional.empty());
        when(taskEngineService.getAllUserTasks(managedAccountHolderId)).thenReturn(List.of());
        when(circleService.getMyCircle(managedAccountHolderId)).thenThrow(new NotFoundException("No active circle found for user"));
        when(creditScoringService.getEligibility(actorUserId, managedAccountHolderId)).thenReturn(new CreditEligibilityResponse(
                managedAccountHolderId,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OffsetDateTime.now()
        ));
        when(appUserRepository.findById(actorUserId)).thenReturn(Optional.of(AppUser.builder()
                .id(actorUserId)
                .firebaseUid("firebase:manager")
                .accountHolderId(managerPrimaryAccountHolderId)
                .role(UserRole.USER)
                .build()));
        when(appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(managedAccountHolderId)).thenReturn(Optional.empty());
        when(prayerSuggestionService.getHomeSuggestion(managedAccount, false, false, false)).thenReturn(null);

        MobileHomeDashboardResponse response = homeFacadeService.getHome(actorUserId, managedAccountHolderId);

        verify(accessControlService).assertCanActOnAccountHolder(actorUserId, managedAccountHolderId, com.ccapp.identity.delegation.domain.enums.PermissionLevel.VIEW);
        assertThat(response.user().accountHolderId()).isEqualTo(managedAccountHolderId);
        assertThat(response.user().managedAccount()).isTrue();
        assertThat(response.user().delegatedAccess()).isTrue();
        assertThat(response.user().firstName()).isEqualTo("Awa");
    }
}
