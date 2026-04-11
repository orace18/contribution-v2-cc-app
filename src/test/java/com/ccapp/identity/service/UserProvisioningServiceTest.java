package com.ccapp.identity.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private UserProvisioningService userProvisioningService;

    @Test
    void provisionNew_shouldBootstrapProvisionalInternationalCountryContext() {
        when(accountHolderRepository.save(any(AccountHolder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser savedUser = userProvisioningService.provisionNew(
                new FirebaseUserClaims("firebase-user-1", "new.member@ccapp.test", "+22961000000")
        );

        ArgumentCaptor<AccountHolder> accountHolderCaptor = ArgumentCaptor.forClass(AccountHolder.class);
        verify(accountHolderRepository).save(accountHolderCaptor.capture());
        AccountHolder savedAccountHolder = accountHolderCaptor.getValue();
        assertThat(savedAccountHolder.getCountryCode()).isEqualTo("INT");
        assertThat(savedAccountHolder.getServiceCountryCode()).isEqualTo("INT");
        assertThat(savedAccountHolder.getFirstName()).isEqualTo("new.member");
        assertThat(savedAccountHolder.getPhoneNumber()).isEqualTo("+22961000000");

        assertThat(savedUser.getFirebaseUid()).isEqualTo("firebase-user-1");
        assertThat(savedUser.getAccountHolderId()).isEqualTo(savedAccountHolder.getId());
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
    }
}
