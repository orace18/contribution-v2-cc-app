package com.ccapp.identity.account_holder.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.service.CountryService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountHolderServiceCountrySelectionTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
    void selectCountry_shouldAllowFirstRealSelectionWhenCurrentCountryIsInt() {
        AccountHolder existing = accountHolder("INT", "INT");
        stubCountrySelection("BJ", "BJ");
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(existing));
        when(accountHolderRepository.save(any(AccountHolder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountHolder saved = accountHolderService.selectCountry(ACCOUNT_HOLDER_ID, "BJ");

        assertThat(saved.getCountryCode()).isEqualTo("BJ");
        assertThat(saved.getServiceCountryCode()).isEqualTo("BJ");
        verify(accountHolderRepository).save(existing);
        verify(countryChartOfAccountsService).findOrCreateWallet(saved);
    }

    @Test
    void selectCountry_shouldAllowFirstSelectionWhenCurrentCountryIsNull() {
        AccountHolder existing = accountHolder(null, "INT");
        stubCountrySelection("BJ", "BJ");
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(existing));
        when(accountHolderRepository.save(any(AccountHolder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountHolder saved = accountHolderService.selectCountry(ACCOUNT_HOLDER_ID, "BJ");

        assertThat(saved.getCountryCode()).isEqualTo("BJ");
        assertThat(saved.getServiceCountryCode()).isEqualTo("BJ");
        verify(accountHolderRepository).save(existing);
        verify(countryChartOfAccountsService).findOrCreateWallet(saved);
    }

    @Test
    void selectCountry_shouldKeepRealCountryImmutable() {
        AccountHolder existing = accountHolder("BJ", "BJ");
        stubCountrySelection("CI", "CI");
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> accountHolderService.selectCountry(ACCOUNT_HOLDER_ID, "CI"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account holder country is immutable");

        verify(accountHolderRepository, never()).save(any(AccountHolder.class));
        verify(countryChartOfAccountsService, never()).findOrCreateWallet(any(AccountHolder.class));
    }

    @Test
    void selectCountry_shouldAllowReselectionOfSameRealCountry() {
        AccountHolder existing = accountHolder("BJ", "BJ");
        stubCountrySelection("BJ", "BJ");
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(existing));
        when(accountHolderRepository.save(any(AccountHolder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountHolder saved = accountHolderService.selectCountry(ACCOUNT_HOLDER_ID, "BJ");

        assertThat(saved.getCountryCode()).isEqualTo("BJ");
        verify(accountHolderRepository).save(existing);
    }

    @Test
    void updateProfile_shouldAllowReplacingProvisionalIntCountry() {
        AccountHolder existing = accountHolder("INT", "INT");
        stubCountrySelection("BJ", "BJ");
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(existing));
        when(accountHolderRepository.existsByPhoneNumberAndIdNot("+22961000000", ACCOUNT_HOLDER_ID)).thenReturn(false);
        when(accountHolderRepository.save(any(AccountHolder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountHolder saved = accountHolderService.updateProfile(
                ACCOUNT_HOLDER_ID,
                "  Jeanne ",
                " Doe  ",
                " +22961000000 ",
                "BJ"
        );

        assertThat(saved.getFirstName()).isEqualTo("Jeanne");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getPhoneNumber()).isEqualTo("+22961000000");
        assertThat(saved.getCountryCode()).isEqualTo("BJ");
        assertThat(saved.getServiceCountryCode()).isEqualTo("BJ");
        verify(countryChartOfAccountsService).findOrCreateWallet(saved);
    }

    private void stubCountrySelection(String selectedCountryCode, String serviceCountryCode) {
        when(countryService.resolveSelectedCountryOrThrow(selectedCountryCode)).thenReturn(country(selectedCountryCode));
        when(countryService.resolveServiceCountryForSelection(selectedCountryCode)).thenReturn(country(serviceCountryCode));
    }

    private Country country(String code) {
        return Country.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(code)
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build();
    }

    private AccountHolder accountHolder(String countryCode, String serviceCountryCode) {
        return AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+22900000000")
                .countryCode(countryCode)
                .serviceCountryCode(serviceCountryCode)
                .build();
    }
}
