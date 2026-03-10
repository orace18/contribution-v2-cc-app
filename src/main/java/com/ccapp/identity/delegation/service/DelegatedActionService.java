package com.ccapp.identity.delegation.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.entity.AccountDelegation;
import com.ccapp.identity.delegation.domain.entity.DelegatedAction;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.DelegationRole;
import com.ccapp.identity.delegation.domain.enums.DelegationStatus;
import com.ccapp.identity.delegation.repository.AccountDelegationRepository;
import com.ccapp.identity.delegation.repository.DelegatedActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelegatedActionService {

    private final DelegatedActionRepository delegatedActionRepository;
    private final AppUserRepository appUserRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountDelegationRepository accountDelegationRepository;

    @Transactional
    public void log(UUID accountHolderId, UUID actorUserId, DelegatedActionType actionType, String payload) {
        log.debug("Identity delegated-action log accountHolderId={} actorUserId={} actionType={}",
                accountHolderId, actorUserId, actionType);
        if (accountHolderId == null || actorUserId == null || actionType == null) {
            log.debug("Identity delegated-action skipped due to null values");
            return;
        }
        delegatedActionRepository.save(DelegatedAction.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .actionType(actionType)
                .payload(payload)
                .build());
        log.info("Identity delegated-action saved accountHolderId={} actorUserId={} actionType={}",
                accountHolderId, actorUserId, actionType);
    }

    @Transactional
    public void logIfDelegatedActor(UUID accountHolderId, UUID actorUserId, DelegatedActionType actionType, String payload) {
        log.debug("Identity delegated-action conditional log accountHolderId={} actorUserId={} actionType={}",
                accountHolderId, actorUserId, actionType);
        if (accountHolderId == null || actorUserId == null || actionType == null) {
            log.debug("Identity delegated-action conditional skipped due to null values");
            return;
        }

        Optional<AppUser> actorAppUser = appUserRepository.findById(actorUserId);
        if (actorAppUser.isEmpty()) {
            log.debug("Identity delegated-action conditional skipped: actor appUser not found actorUserId={}", actorUserId);
            return;
        }
        if (accountHolderId.equals(actorAppUser.get().getAccountHolderId())) {
            log.debug("Identity delegated-action conditional skipped: actor is owner accountHolderId={} actorUserId={}",
                    accountHolderId, actorUserId);
            return;
        }

        Optional<AccountHolder> targetAccountHolder = accountHolderRepository.findById(accountHolderId);
        if (targetAccountHolder.isEmpty() || targetAccountHolder.get().getAccountType() != AccountType.ASSISTED) {
            log.debug("Identity delegated-action conditional skipped: target not assisted accountHolderId={}", accountHolderId);
            return;
        }

        Optional<AccountDelegation> delegation = accountDelegationRepository
                .findFirstByAccountHolderIdAndDelegateUserIdAndStatusOrderByCreatedAtDesc(
                        accountHolderId,
                        actorUserId,
                        DelegationStatus.ACTIVE
                );
        if (delegation.isEmpty()) {
            log.debug("Identity delegated-action conditional skipped: no active delegation accountHolderId={} actorUserId={}",
                    accountHolderId, actorUserId);
            return;
        }

        AccountDelegation activeDelegation = delegation.get();
        if (activeDelegation.getRole() != DelegationRole.MANAGER) {
            log.debug("Identity delegated-action conditional skipped: role not MANAGER delegationId={} role={}",
                    activeDelegation.getId(), activeDelegation.getRole());
            return;
        }
        if (activeDelegation.getExpiresAt() != null && activeDelegation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.debug("Identity delegated-action conditional skipped: delegation expired delegationId={}", activeDelegation.getId());
            return;
        }

        log(accountHolderId, actorUserId, actionType, payload);
    }
}
