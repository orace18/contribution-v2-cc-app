package com.ccapp.identity.delegation.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.delegation.domain.entity.AccountDelegation;
import com.ccapp.identity.delegation.domain.enums.DelegationRole;
import com.ccapp.identity.delegation.domain.enums.DelegationStatus;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.repository.AccountDelegationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegationService {

    private final AccountDelegationRepository accountDelegationRepository;
    private final AccountHolderService accountHolderService;
    private final AppUserRepository appUserRepository;

    @Transactional
    public AccountDelegation createDelegation(UUID accountHolderId,
                                              UUID delegateUserId,
                                              PermissionLevel permissionLevel,
                                              OffsetDateTime expiresAt) {
        accountHolderService.getById(accountHolderId);
        appUserRepository.findById(delegateUserId)
                .orElseThrow(() -> new NotFoundException("Delegate app user not found"));

        return accountDelegationRepository.save(AccountDelegation.builder()
                .accountHolderId(accountHolderId)
                .delegateUserId(delegateUserId)
                .permissionLevel(permissionLevel)
                .role(DelegationRole.MANAGER)
                .status(DelegationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build());
    }

    @Transactional
    public AccountDelegation createManagerDelegation(UUID accountHolderId, UUID delegateUserId) {
        return createDelegation(accountHolderId, delegateUserId, PermissionLevel.FULL, null);
    }

    @Transactional(readOnly = true)
    public Optional<PermissionLevel> getPermission(UUID accountHolderId, UUID delegateUserId) {
        Optional<AccountDelegation> latest = accountDelegationRepository
                .findFirstByAccountHolderIdAndDelegateUserIdAndStatusOrderByCreatedAtDesc(
                        accountHolderId,
                        delegateUserId,
                        DelegationStatus.ACTIVE
                );
        if (latest.isEmpty()) {
            return Optional.empty();
        }

        AccountDelegation delegation = latest.get();
        if (delegation.getExpiresAt() != null && delegation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(delegation.getPermissionLevel());
    }

    @Transactional(readOnly = true)
    public List<AccountDelegation> getActiveDelegationsForDelegate(UUID delegateUserId) {
        List<AccountDelegation> result = new ArrayList<>();
        result.addAll(accountDelegationRepository.findByDelegateUserIdAndStatusAndExpiresAtIsNull(delegateUserId, DelegationStatus.ACTIVE));
        result.addAll(accountDelegationRepository.findByDelegateUserIdAndStatusAndExpiresAtAfter(delegateUserId, DelegationStatus.ACTIVE, OffsetDateTime.now()));
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<AccountDelegation> findActiveManagerDelegation(UUID accountHolderId) {
        return accountDelegationRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(
                accountHolderId,
                DelegationStatus.ACTIVE
        ).filter(delegation -> delegation.getRole() == DelegationRole.MANAGER)
                .filter(delegation -> delegation.getExpiresAt() == null || delegation.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional
    public void deactivateActiveDelegations(UUID accountHolderId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<AccountDelegation> activeDelegations = accountDelegationRepository.findByAccountHolderIdAndStatus(
                accountHolderId,
                DelegationStatus.ACTIVE
        );
        activeDelegations.forEach(delegation -> {
            delegation.setStatus(DelegationStatus.REVOKED);
            delegation.setExpiresAt(now);
        });
        accountDelegationRepository.saveAll(activeDelegations);
    }
}
