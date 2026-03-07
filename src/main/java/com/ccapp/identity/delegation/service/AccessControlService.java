package com.ccapp.identity.delegation.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final AppUserRepository appUserRepository;
    private final DelegationService delegationService;

    @Transactional(readOnly = true)
    public boolean canActOnAccountHolder(UUID actorUserId, UUID accountHolderId, PermissionLevel requiredPermission) {
        if (actorUserId == null || accountHolderId == null) {
            return false;
        }
        if (actorUserId.equals(accountHolderId)) {
            return true;
        }

        AppUser actor = appUserRepository.findById(actorUserId).orElse(null);
        if (actor != null && accountHolderId.equals(actor.getAccountHolderId())) {
            return true;
        }

        return delegationService.getPermission(accountHolderId, actorUserId)
                .map(level -> level.allows(requiredPermission))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void assertCanActOnAccountHolder(UUID actorUserId, UUID accountHolderId, PermissionLevel requiredPermission) {
        if (!canActOnAccountHolder(actorUserId, accountHolderId, requiredPermission)) {
            throw new UnauthorizedException("Delegated access denied");
        }
    }
}
