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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationService {

    private final AccountDelegationRepository accountDelegationRepository;
    private final AccountHolderService accountHolderService;
    private final AppUserRepository appUserRepository;

    @Transactional
    public AccountDelegation createDelegation(UUID accountHolderId,
                                              UUID delegateUserId,
                                              PermissionLevel permissionLevel,
                                              OffsetDateTime expiresAt) {
        log.info("Identity delegation create accountHolderId={} delegateUserId={} permissionLevel={} expiresAt={}",
                accountHolderId, delegateUserId, permissionLevel, expiresAt);
        accountHolderService.getById(accountHolderId);
        appUserRepository.findById(delegateUserId)
                .orElseThrow(() -> new NotFoundException("Delegate app user not found"));

        AccountDelegation delegation = accountDelegationRepository.save(AccountDelegation.builder()
                .accountHolderId(accountHolderId)
                .delegateUserId(delegateUserId)
                .permissionLevel(permissionLevel)
                .role(DelegationRole.MANAGER)
                .status(DelegationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build());
        log.info("Identity delegation created id={} accountHolderId={} delegateUserId={}",
                delegation.getId(), delegation.getAccountHolderId(), delegation.getDelegateUserId());
        return delegation;
    }

    @Transactional
    public AccountDelegation createManagerDelegation(UUID accountHolderId, UUID delegateUserId) {
        log.info("Identity delegation createManagerDelegation accountHolderId={} delegateUserId={}", accountHolderId, delegateUserId);
        return createDelegation(accountHolderId, delegateUserId, PermissionLevel.FULL, null);
    }

    @Transactional(readOnly = true)
    public Optional<PermissionLevel> getPermission(UUID accountHolderId, UUID delegateUserId) {
        log.debug("Identity delegation getPermission accountHolderId={} delegateUserId={}", accountHolderId, delegateUserId);
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
            log.debug("Identity delegation expired accountHolderId={} delegateUserId={} delegationId={}",
                    accountHolderId, delegateUserId, delegation.getId());
            return Optional.empty();
        }
        return Optional.of(delegation.getPermissionLevel());
    }

    @Transactional(readOnly = true)
    public List<AccountDelegation> getActiveDelegationsForDelegate(UUID delegateUserId) {
        log.debug("Identity delegation getActiveDelegationsForDelegate delegateUserId={}", delegateUserId);
        List<AccountDelegation> result = new ArrayList<>();
        result.addAll(accountDelegationRepository.findByDelegateUserIdAndStatusAndExpiresAtIsNull(delegateUserId, DelegationStatus.ACTIVE));
        result.addAll(accountDelegationRepository.findByDelegateUserIdAndStatusAndExpiresAtAfter(delegateUserId, DelegationStatus.ACTIVE, OffsetDateTime.now()));
        log.debug("Identity delegation active count delegateUserId={} count={}", delegateUserId, result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<AccountDelegation> findActiveManagerDelegation(UUID accountHolderId) {
        log.debug("Identity delegation findActiveManagerDelegation accountHolderId={}", accountHolderId);
        return accountDelegationRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(
                accountHolderId,
                DelegationStatus.ACTIVE
        ).filter(delegation -> delegation.getRole() == DelegationRole.MANAGER)
                .filter(delegation -> delegation.getExpiresAt() == null || delegation.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    public Optional<AccountDelegation> findActiveManagerDelegation(UUID accountHolderId, UUID delegateUserId) {
        log.debug("Identity delegation findActiveManagerDelegation accountHolderId={} delegateUserId={}",
                accountHolderId, delegateUserId);
        return accountDelegationRepository
                .findFirstByAccountHolderIdAndDelegateUserIdAndRoleAndStatusOrderByCreatedAtDesc(
                        accountHolderId,
                        delegateUserId,
                        DelegationRole.MANAGER,
                        DelegationStatus.ACTIVE
                )
                .filter(delegation -> delegation.getExpiresAt() == null || delegation.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional
    public void deactivateActiveDelegations(UUID accountHolderId) {
        log.info("Identity delegation deactivateActiveDelegations accountHolderId={}", accountHolderId);
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
        log.info("Identity delegation deactivated accountHolderId={} count={}", accountHolderId, activeDelegations.size());
    }

    @Transactional
    public void revokeActiveManagerDelegation(UUID accountHolderId, UUID delegateUserId) {
        log.info("Identity delegation revokeActiveManagerDelegation accountHolderId={} delegateUserId={}",
                accountHolderId, delegateUserId);
        findActiveManagerDelegation(accountHolderId, delegateUserId).ifPresent(delegation -> {
            delegation.setStatus(DelegationStatus.REVOKED);
            delegation.setExpiresAt(OffsetDateTime.now());
            accountDelegationRepository.save(delegation);
            log.info("Identity delegation revoked delegationId={} accountHolderId={} delegateUserId={}",
                    delegation.getId(), accountHolderId, delegateUserId);
        });
    }
}
