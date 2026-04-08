package com.ccapp.identity.managedaccounts.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.entity.AccountDelegation;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.DelegationRole;
import com.ccapp.identity.delegation.domain.enums.DelegationStatus;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.delegation.service.DelegationService;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.managedaccounts.dto.CreateManagedAccountRequest;
import com.ccapp.identity.managedaccounts.dto.LinkExistingManagedAccountRequest;
import com.ccapp.identity.managedaccounts.dto.ManagedAccountDetailResponse;
import com.ccapp.identity.managedaccounts.dto.ManagedAccountSummaryResponse;
import com.ccapp.identity.managedaccounts.dto.UpdateManagedAccountRequest;
import com.ccapp.identity.verification.service.PhoneVerificationService;
import com.ccapp.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedAccountsServiceTest {

    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private DelegationService delegationService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private PhoneVerificationService phoneVerificationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DelegatedActionService delegatedActionService;

    @InjectMocks
    private ManagedAccountsService managedAccountsService;

    @Test
    void createManagedAccount_shouldVerifyPhoneCreateHolderAndDelegation() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        CreateManagedAccountRequest request = new CreateManagedAccountRequest(
                "Marie",
                "Kouassi",
                "+22961000000",
                "BJ",
                verificationId
        );
        AccountHolder created = assistedAccount(managedAccountId, "Marie", "Kouassi", "+22961000000");
        AccountDelegation delegation = delegation(managedAccountId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now());

        when(accountHolderService.createManagedAccount(
                actorUserId,
                "Marie",
                "Kouassi",
                "+22961000000",
                "BJ"
        )).thenReturn(created);
        when(delegationService.createManagerDelegation(managedAccountId, actorUserId)).thenReturn(delegation);
        when(notificationService.getUnreadCount(managedAccountId)).thenReturn(3L);

        ManagedAccountDetailResponse response = managedAccountsService.createManagedAccount(actorUserId, actorAccountHolderId, request);

        verify(phoneVerificationService).assertVerifiedForPhone(actorAccountHolderId, "+22961000000", verificationId);
        verify(delegationService).deactivateActiveDelegations(managedAccountId);
        verify(delegationService).createManagerDelegation(managedAccountId, actorUserId);
        assertThat(response.accountHolderId()).isEqualTo(managedAccountId);
        assertThat(response.createdVia()).isEqualTo(AccountCreatedVia.MANAGED_ACCOUNT);
        assertThat(response.managerUserId()).isEqualTo(actorUserId);
        assertThat(response.unreadNotificationCount()).isEqualTo(3L);
    }

    @Test
    void listManagedAccounts_shouldReturnOnlyAssistedAccountsForActiveDelegations() {
        UUID actorUserId = UUID.randomUUID();
        UUID assistedId = UUID.randomUUID();
        UUID standardId = UUID.randomUUID();
        AccountDelegation assistedDelegation = delegation(assistedId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now().minusDays(1));
        AccountDelegation standardDelegation = delegation(standardId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now().minusDays(2));
        AccountHolder assisted = assistedAccount(assistedId, "Awa", "Doe", "+22961000001");
        AccountHolder standard = standardAccount(standardId, "Owner", "User");

        when(delegationService.getActiveDelegationsForDelegate(actorUserId)).thenReturn(List.of(assistedDelegation, standardDelegation));
        when(accountHolderRepository.findAllById(List.of(assistedId, standardId))).thenReturn(List.of(assisted, standard));
        when(notificationService.getUnreadCount(assistedId)).thenReturn(1L);

        List<ManagedAccountSummaryResponse> response = managedAccountsService.listManagedAccounts(actorUserId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).accountHolderId()).isEqualTo(assistedId);
        assertThat(response.get(0).displayName()).isEqualTo("Awa Doe");
        assertThat(response.get(0).unreadNotificationCount()).isEqualTo(1L);
    }

    @Test
    void getManagedAccountDetail_shouldRequireActiveManagerDelegation() {
        UUID actorUserId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        when(accountHolderService.getById(managedAccountId)).thenReturn(assistedAccount(managedAccountId, "Awa", "Doe", "+22961000001"));
        when(delegationService.findActiveManagerDelegation(managedAccountId, actorUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> managedAccountsService.getManagedAccountDetail(actorUserId, managedAccountId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Managed account access denied");
    }

    @Test
    void updateManagedAccount_shouldRequirePhoneVerificationWhenPhoneChanges() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        AccountHolder existing = assistedAccount(managedAccountId, "Awa", "Doe", "+22961000001");
        AccountHolder updated = assistedAccount(managedAccountId, "Awa", "Doe", "+22961000002");
        AccountDelegation delegation = delegation(managedAccountId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now().minusDays(2));
        UpdateManagedAccountRequest request = new UpdateManagedAccountRequest(
                "Awa",
                "Doe",
                "+22961000002",
                "BJ",
                verificationId
        );

        when(accountHolderService.getById(managedAccountId)).thenReturn(existing);
        when(delegationService.findActiveManagerDelegation(managedAccountId, actorUserId)).thenReturn(Optional.of(delegation));
        when(accountHolderService.updateProfile(managedAccountId, "Awa", "Doe", "+22961000002", "BJ")).thenReturn(updated);
        when(notificationService.getUnreadCount(managedAccountId)).thenReturn(0L);

        ManagedAccountDetailResponse response = managedAccountsService.updateManagedAccount(
                actorUserId,
                actorAccountHolderId,
                managedAccountId,
                request
        );

        verify(accessControlService).assertCanActOnAccountHolder(actorUserId, managedAccountId, PermissionLevel.FULL);
        verify(phoneVerificationService).assertVerifiedForPhone(actorAccountHolderId, "+22961000002", verificationId);
        verify(delegatedActionService).log(managedAccountId, actorUserId, DelegatedActionType.PROFILE_UPDATED, "managed_account_profile_updated");
        assertThat(response.phoneNumber()).isEqualTo("+22961000002");
    }

    @Test
    void updateManagedAccount_shouldNotRequirePhoneVerificationWhenPhoneUnchanged() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        AccountHolder existing = assistedAccount(managedAccountId, "Awa", "Doe", "+22961000001");
        AccountDelegation delegation = delegation(managedAccountId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now().minusDays(2));
        UpdateManagedAccountRequest request = new UpdateManagedAccountRequest(
                "Awa",
                "Doe",
                "+22961000001",
                "BJ",
                null
        );

        when(accountHolderService.getById(managedAccountId)).thenReturn(existing);
        when(delegationService.findActiveManagerDelegation(managedAccountId, actorUserId)).thenReturn(Optional.of(delegation));
        when(accountHolderService.updateProfile(managedAccountId, "Awa", "Doe", "+22961000001", "BJ")).thenReturn(existing);
        when(notificationService.getUnreadCount(managedAccountId)).thenReturn(0L);

        managedAccountsService.updateManagedAccount(actorUserId, actorAccountHolderId, managedAccountId, request);

        verify(phoneVerificationService, never()).assertVerifiedForPhone(any(), any(), any());
    }

    @Test
    void revokeManagedAccount_shouldOnlyRevokeDelegation() {
        UUID actorUserId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        AccountHolder existing = assistedAccount(managedAccountId, "Awa", "Doe", "+22961000001");
        AccountDelegation delegation = delegation(managedAccountId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now().minusDays(4));

        when(accountHolderService.getById(managedAccountId)).thenReturn(existing);
        when(delegationService.findActiveManagerDelegation(managedAccountId, actorUserId)).thenReturn(Optional.of(delegation));

        managedAccountsService.revokeManagedAccount(actorUserId, managedAccountId);

        verify(accessControlService).assertCanActOnAccountHolder(actorUserId, managedAccountId, PermissionLevel.FULL);
        verify(delegationService).revokeActiveManagerDelegation(managedAccountId, actorUserId);
        verify(accountHolderRepository, never()).deleteById(any());
    }

    @Test
    void linkExistingManagedAccount_shouldCreateDelegationWhenManagedAccountIsAvailable() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        LinkExistingManagedAccountRequest request = new LinkExistingManagedAccountRequest(
                "+22961000009",
                verificationId
        );
        AccountHolder existing = assistedAccount(managedAccountId, "Joelle", "Sossa", "+22961000009");
        AccountDelegation delegation = delegation(managedAccountId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now());

        when(accountHolderRepository.findByPhoneNumber("+22961000009")).thenReturn(Optional.of(existing));
        when(delegationService.findActiveManagerDelegation(managedAccountId)).thenReturn(Optional.empty());
        when(delegationService.createManagerDelegation(managedAccountId, actorUserId)).thenReturn(delegation);
        when(notificationService.getUnreadCount(managedAccountId)).thenReturn(2L);

        ManagedAccountDetailResponse response = managedAccountsService.linkExistingManagedAccount(
                actorUserId,
                actorAccountHolderId,
                request
        );

        verify(phoneVerificationService).assertVerifiedForPhone(actorAccountHolderId, "+22961000009", verificationId);
        verify(delegationService).createManagerDelegation(managedAccountId, actorUserId);
        assertThat(response.accountHolderId()).isEqualTo(managedAccountId);
        assertThat(response.unreadNotificationCount()).isEqualTo(2L);
    }

    @Test
    void linkExistingManagedAccount_shouldBeIdempotentForSameManager() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        LinkExistingManagedAccountRequest request = new LinkExistingManagedAccountRequest(
                "+22961000010",
                verificationId
        );
        AccountHolder existing = assistedAccount(managedAccountId, "Joelle", "Sossa", "+22961000010");
        AccountDelegation delegation = delegation(managedAccountId, actorUserId, PermissionLevel.FULL, OffsetDateTime.now().minusDays(1));

        when(accountHolderRepository.findByPhoneNumber("+22961000010")).thenReturn(Optional.of(existing));
        when(delegationService.findActiveManagerDelegation(managedAccountId)).thenReturn(Optional.of(delegation));
        when(notificationService.getUnreadCount(managedAccountId)).thenReturn(1L);

        ManagedAccountDetailResponse response = managedAccountsService.linkExistingManagedAccount(
                actorUserId,
                actorAccountHolderId,
                request
        );

        verify(phoneVerificationService).assertVerifiedForPhone(actorAccountHolderId, "+22961000010", verificationId);
        verify(accessControlService).assertCanActOnAccountHolder(actorUserId, managedAccountId, PermissionLevel.VIEW);
        verify(delegationService, never()).createManagerDelegation(any(), any());
        assertThat(response.accountHolderId()).isEqualTo(managedAccountId);
    }

    @Test
    void linkExistingManagedAccount_shouldRejectWhenAnotherManagerIsAlreadyActive() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID managedAccountId = UUID.randomUUID();
        LinkExistingManagedAccountRequest request = new LinkExistingManagedAccountRequest(
                "+22961000011",
                verificationId
        );
        AccountHolder existing = assistedAccount(managedAccountId, "Joelle", "Sossa", "+22961000011");
        AccountDelegation otherManager = delegation(managedAccountId, UUID.randomUUID(), PermissionLevel.FULL, OffsetDateTime.now().minusDays(1));

        when(accountHolderRepository.findByPhoneNumber("+22961000011")).thenReturn(Optional.of(existing));
        when(delegationService.findActiveManagerDelegation(managedAccountId)).thenReturn(Optional.of(otherManager));

        assertThatThrownBy(() -> managedAccountsService.linkExistingManagedAccount(
                actorUserId,
                actorAccountHolderId,
                request
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MANAGED_ACCOUNT_ALREADY_LINKED");
    }

    @Test
    void linkExistingManagedAccount_shouldRejectNonAssistedAccount() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        LinkExistingManagedAccountRequest request = new LinkExistingManagedAccountRequest(
                "+22961000012",
                verificationId
        );

        when(accountHolderRepository.findByPhoneNumber("+22961000012"))
                .thenReturn(Optional.of(standardAccount(UUID.randomUUID(), "Owner", "User")));

        assertThatThrownBy(() -> managedAccountsService.linkExistingManagedAccount(
                actorUserId,
                actorAccountHolderId,
                request
        ))
                .isInstanceOf(com.ccapp.common.exception.BusinessException.class)
                .hasMessageContaining("MANAGED_ACCOUNT_REQUIRED");
    }

    private AccountHolder assistedAccount(UUID accountHolderId, String firstName, String lastName, String phoneNumber) {
        return AccountHolder.builder()
                .id(accountHolderId)
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber(phoneNumber)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.ASSISTED)
                .createdByUserId(UUID.randomUUID())
                .createdVia(AccountCreatedVia.MANAGED_ACCOUNT)
                .kycStatus(KycStatus.NOT_STARTED)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .createdAt(OffsetDateTime.now().minusDays(3))
                .build();
    }

    private AccountHolder standardAccount(UUID accountHolderId, String firstName, String lastName) {
        return AccountHolder.builder()
                .id(accountHolderId)
                .firstName(firstName)
                .lastName(lastName)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .createdVia(AccountCreatedVia.SELF)
                .kycStatus(KycStatus.NOT_STARTED)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .createdAt(OffsetDateTime.now().minusDays(10))
                .build();
    }

    private AccountDelegation delegation(UUID accountHolderId,
                                         UUID delegateUserId,
                                         PermissionLevel permissionLevel,
                                         OffsetDateTime createdAt) {
        return AccountDelegation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .delegateUserId(delegateUserId)
                .permissionLevel(permissionLevel)
                .role(DelegationRole.MANAGER)
                .status(DelegationStatus.ACTIVE)
                .createdAt(createdAt)
                .build();
    }
}
