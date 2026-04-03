package com.ccapp.commerce.service;

import com.ccapp.commerce.domain.entity.CommerceMerchant;
import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;
import com.ccapp.commerce.repository.CommerceMerchantRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceMerchantService {

    private final CommerceMerchantRepository commerceMerchantRepository;
    private final AccountHolderService accountHolderService;
    private final AccountHolderRepository accountHolderRepository;

    @Transactional
    public CommerceMerchant ensureMerchant(UUID accountHolderId) {
        return commerceMerchantRepository.findByAccountHolderId(accountHolderId)
                .orElseGet(() -> {
                    AccountHolder holder = accountHolderService.getById(accountHolderId);
                    return commerceMerchantRepository.save(CommerceMerchant.builder()
                            .id(UUID.randomUUID())
                            .accountHolderId(accountHolderId)
                            .displayName(displayName(holder))
                            .countryCode(readCountryCode(holder))
                            .status(CommerceMerchantStatus.ACTIVE)
                            .build());
                });
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> resolveDisplayNames(Collection<UUID> merchantAccountHolderIds) {
        Map<UUID, String> namesByMerchantId = new HashMap<>();
        if (merchantAccountHolderIds == null || merchantAccountHolderIds.isEmpty()) {
            return namesByMerchantId;
        }

        commerceMerchantRepository.findByAccountHolderIdIn(merchantAccountHolderIds)
                .forEach(merchant -> namesByMerchantId.put(merchant.getAccountHolderId(), merchant.getDisplayName()));

        accountHolderRepository.findAllById(merchantAccountHolderIds)
                .forEach(holder -> namesByMerchantId.putIfAbsent(holder.getId(), displayName(holder)));

        return namesByMerchantId;
    }

    private String displayName(AccountHolder holder) {
        if (holder == null) {
            return null;
        }
        String firstName = holder.getFirstName() == null ? "" : holder.getFirstName().trim();
        String lastName = holder.getLastName() == null ? "" : holder.getLastName().trim();
        String display = (firstName + " " + lastName).trim();
        return display.isBlank() ? holder.getId().toString() : display;
    }

    private String readCountryCode(AccountHolder holder) {
        if (holder == null) {
            return null;
        }
        if (holder.getServiceCountryCode() != null && !holder.getServiceCountryCode().isBlank()) {
            return holder.getServiceCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        if (holder.getCountryCode() != null && !holder.getCountryCode().isBlank()) {
            return holder.getCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }
}
