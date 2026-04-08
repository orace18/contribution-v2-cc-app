package com.ccapp.identity.managedaccounts.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagedAccountReadContextServiceTest {

    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private ManagedAccountReadContextService managedAccountReadContextService;

    @Test
    void resolveViewTargetAccountHolderId_shouldKeepDefaultSelfTargetWhenNoOverrideIsProvided() {
        UUID actorUserId = UUID.randomUUID();
        UUID defaultAccountHolderId = UUID.randomUUID();

        UUID resolved = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                defaultAccountHolderId,
                null
        );

        assertThat(resolved).isEqualTo(defaultAccountHolderId);
        verify(accountHolderService).getById(defaultAccountHolderId);
        verify(accessControlService).assertCanActOnAccountHolder(actorUserId, defaultAccountHolderId, PermissionLevel.VIEW);
    }

    @Test
    void resolveViewTargetAccountHolderId_shouldAuthorizeExplicitManagedTarget() {
        UUID actorUserId = UUID.randomUUID();
        UUID defaultAccountHolderId = UUID.randomUUID();
        UUID managedAccountHolderId = UUID.randomUUID();

        UUID resolved = managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                defaultAccountHolderId,
                managedAccountHolderId
        );

        assertThat(resolved).isEqualTo(managedAccountHolderId);
        verify(accountHolderService).getById(managedAccountHolderId);
        verify(accessControlService).assertCanActOnAccountHolder(actorUserId, managedAccountHolderId, PermissionLevel.VIEW);
    }

    @Test
    void resolveViewTargetAccountHolderId_shouldRejectUnauthorizedManagedTarget() {
        UUID actorUserId = UUID.randomUUID();
        UUID defaultAccountHolderId = UUID.randomUUID();
        UUID managedAccountHolderId = UUID.randomUUID();
        doThrow(new UnauthorizedException("Delegated access denied"))
                .when(accessControlService)
                .assertCanActOnAccountHolder(actorUserId, managedAccountHolderId, PermissionLevel.VIEW);

        assertThatThrownBy(() -> managedAccountReadContextService.resolveViewTargetAccountHolderId(
                actorUserId,
                defaultAccountHolderId,
                managedAccountHolderId
        ))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Delegated access denied");
    }
}
