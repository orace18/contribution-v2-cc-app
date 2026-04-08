package com.ccapp.identity.account_holder.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.service.CountryService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountHolderServiceManagedAccountTest {

    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private CountryService countryService;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private AccountHolderService accountHolderService;

    @Test
    void createManagedAccount_shouldCreateAssistedAccountHolderAndBootstrapWallet() {
        UUID managerUserId = UUID.randomUUID();
        Country selectedCountry = Country.builder()
                .id(UUID.randomUUID())
                .code("BJ")
                .name("Benin")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build();

        when(countryService.resolveSelectedCountryOrThrow("BJ")).thenReturn(selectedCountry);
        when(countryService.resolveServiceCountryForSelection("BJ")).thenReturn(selectedCountry);
        when(accountHolderRepository.existsByPhoneNumber("+22961000000")).thenReturn(false);
        when(accountHolderRepository.save(any(AccountHolder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountHolder created = accountHolderService.createManagedAccount(
                managerUserId,
                "  Marie ",
                " Kouassi  ",
                " +22961000000 ",
                "BJ"
        );

        ArgumentCaptor<AccountHolder> captor = ArgumentCaptor.forClass(AccountHolder.class);
        verify(accountHolderRepository).save(captor.capture());
        AccountHolder saved = captor.getValue();
        assertThat(created.getId()).isEqualTo(saved.getId());
        assertThat(saved.getFirstName()).isEqualTo("Marie");
        assertThat(saved.getLastName()).isEqualTo("Kouassi");
        assertThat(saved.getPhoneNumber()).isEqualTo("+22961000000");
        assertThat(saved.getCountryCode()).isEqualTo("BJ");
        assertThat(saved.getServiceCountryCode()).isEqualTo("BJ");
        assertThat(saved.getAccountType()).isEqualTo(AccountType.ASSISTED);
        assertThat(saved.getCreatedVia()).isEqualTo(AccountCreatedVia.MANAGED_ACCOUNT);
        assertThat(saved.getCreatedByUserId()).isEqualTo(managerUserId);
        assertThat(saved.getKycStatus()).isEqualTo(KycStatus.NOT_STARTED);
        assertThat(saved.getKycLevel()).isEqualTo(KycLevel.LEVEL_0_UNVERIFIED);
        verify(countryChartOfAccountsService).findOrCreateWallet(saved);
        verify(domainEventPublisher).publish(any());
    }
}
