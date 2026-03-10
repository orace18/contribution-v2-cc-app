package com.ccapp.identity.delegation.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessControlService {

    private static final String CROSS_COUNTRY_DELEGATION_NOT_ALLOWED = "CROSS_COUNTRY_DELEGATION_NOT_ALLOWED";

    private final AppUserRepository appUserRepository;
    private final DelegationService delegationService;
    private final CountryContextService countryContextService;

    @Transactional(readOnly = true)
    public boolean canActOnAccountHolder(UUID actorUserId, UUID accountHolderId, PermissionLevel requiredPermission) {
        log.debug("Identity access-control canAct actorUserId={} accountHolderId={} permission={}",
                actorUserId, accountHolderId, requiredPermission);
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
        log.debug("Identity access-control assert actorUserId={} accountHolderId={} permission={}",
                actorUserId, accountHolderId, requiredPermission);
        if (!canActOnAccountHolder(actorUserId, accountHolderId, requiredPermission)) {
            log.warn("Identity access-control denied actorUserId={} accountHolderId={} permission={}",
                    actorUserId, accountHolderId, requiredPermission);
            throw new UnauthorizedException("Delegated access denied");
        }
        assertSameServiceCountryForDelegation(actorUserId, accountHolderId);
    }

    private void assertSameServiceCountryForDelegation(UUID actorUserId, UUID targetAccountHolderId) {
        if (actorUserId == null || targetAccountHolderId == null) {
            return;
        }

        AppUser actor = appUserRepository.findById(actorUserId).orElse(null);
        if (actor == null || targetAccountHolderId.equals(actor.getAccountHolderId())) {
            return;
        }

        String actorServiceCountry = countryContextService.resolve(actor.getAccountHolderId()).serviceCountryCode();
        String targetServiceCountry = countryContextService.resolve(targetAccountHolderId).serviceCountryCode();
        if (!actorServiceCountry.equalsIgnoreCase(targetServiceCountry)) {
            log.warn("Identity access-control denied cross-country delegation actorUserId={} actorServiceCountry={} targetAccountHolderId={} targetServiceCountry={}",
                    actorUserId, actorServiceCountry, targetAccountHolderId, targetServiceCountry);
            throw new BusinessException(CROSS_COUNTRY_DELEGATION_NOT_ALLOWED);
        }
    }
}
