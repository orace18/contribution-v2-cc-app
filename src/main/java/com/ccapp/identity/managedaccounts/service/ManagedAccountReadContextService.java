package com.ccapp.identity.managedaccounts.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagedAccountReadContextService {

    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public UUID resolveViewTargetAccountHolderId(UUID actorUserId,
                                                 UUID defaultAccountHolderId,
                                                 @Nullable UUID requestedAccountHolderId) {
        UUID resolvedAccountHolderId = requestedAccountHolderId != null
                ? requestedAccountHolderId
                : defaultAccountHolderId;
        log.info("Managed account read-context resolve actorUserId={} defaultAccountHolderId={} requestedAccountHolderId={} resolvedAccountHolderId={}",
                actorUserId, defaultAccountHolderId, requestedAccountHolderId, resolvedAccountHolderId);
        accountHolderService.getById(resolvedAccountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, resolvedAccountHolderId, PermissionLevel.VIEW);
        return resolvedAccountHolderId;
    }
}
