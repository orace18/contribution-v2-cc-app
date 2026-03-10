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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegatedActionService {

    private final DelegatedActionRepository delegatedActionRepository;
    private final AppUserRepository appUserRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountDelegationRepository accountDelegationRepository;

    @Transactional
    public void log(UUID accountHolderId, UUID actorUserId, DelegatedActionType actionType, String payload) {
        if (accountHolderId == null || actorUserId == null || actionType == null) {
            return;
        }
        delegatedActionRepository.save(DelegatedAction.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .actionType(actionType)
                .payload(payload)
                .build());
    }

    @Transactional
    public void logIfDelegatedActor(UUID accountHolderId, UUID actorUserId, DelegatedActionType actionType, String payload) {
        if (accountHolderId == null || actorUserId == null || actionType == null) {
            return;
        }

        Optional<AppUser> actorAppUser = appUserRepository.findById(actorUserId);
        if (actorAppUser.isEmpty()) {
            return;
        }
        if (accountHolderId.equals(actorAppUser.get().getAccountHolderId())) {
            return;
        }

        Optional<AccountHolder> targetAccountHolder = accountHolderRepository.findById(accountHolderId);
        if (targetAccountHolder.isEmpty() || targetAccountHolder.get().getAccountType() != AccountType.ASSISTED) {
            return;
        }

        Optional<AccountDelegation> delegation = accountDelegationRepository
                .findFirstByAccountHolderIdAndDelegateUserIdAndStatusOrderByCreatedAtDesc(
                        accountHolderId,
                        actorUserId,
                        DelegationStatus.ACTIVE
                );
        if (delegation.isEmpty()) {
            return;
        }

        AccountDelegation activeDelegation = delegation.get();
        if (activeDelegation.getRole() != DelegationRole.MANAGER) {
            return;
        }
        if (activeDelegation.getExpiresAt() != null && activeDelegation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return;
        }

        log(accountHolderId, actorUserId, actionType, payload);
    }
}
