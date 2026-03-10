package com.ccapp.country.service;

import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CountryContextService {

    private final AccountHolderService accountHolderService;
    private final CountryService countryService;

    @Transactional(readOnly = true)
    public CountryContext resolve(UUID accountHolderId) {
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        Country serviceCountry = countryService.resolveServiceCountryByCodeOrFallback(accountHolder.getServiceCountryCode());
        String countryCode = accountHolder.getCountryCode() == null || accountHolder.getCountryCode().isBlank()
                ? serviceCountry.getCode()
                : accountHolder.getCountryCode();
        return new CountryContext(countryCode, serviceCountry.getCode(), serviceCountry.getCurrency());
    }
}
