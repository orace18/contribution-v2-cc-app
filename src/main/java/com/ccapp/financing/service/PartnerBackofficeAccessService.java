package com.ccapp.financing.service;

import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.financing.domain.entity.PartnerUser;
import com.ccapp.financing.domain.enums.PartnerBackofficeRole;
import com.ccapp.financing.domain.enums.PartnerUserStatus;
import com.ccapp.financing.repository.PartnerRoleRepository;
import com.ccapp.financing.repository.PartnerUserRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerBackofficeAccessService {

    private final PartnerUserRepository partnerUserRepository;
    private final PartnerRoleRepository partnerRoleRepository;
    private final AccountHolderService accountHolderService;

    @Transactional(readOnly = true)
    public void assertHasAnyRole(UUID accountHolderId, UUID partnerId, Collection<PartnerBackofficeRole> roles) {
        PartnerUser partnerUser = resolveActivePartnerUser(accountHolderId, partnerId);
        boolean hasRole = partnerRoleRepository.existsByPartnerUserIdAndRoleIn(partnerUser.getId(), roles);
        if (!hasRole) {
            throw new UnauthorizedException("Partner role does not permit this action");
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessPartner(UUID accountHolderId, UUID partnerId) {
        return partnerUserRepository.existsByPartnerIdAndAccountHolderIdAndStatus(
                partnerId,
                accountHolderId,
                PartnerUserStatus.ACTIVE
        );
    }

    @Transactional(readOnly = true)
    public Set<UUID> resolveAccessiblePartnerIds(UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        return partnerUserRepository.findByAccountHolderIdAndStatus(accountHolderId, PartnerUserStatus.ACTIVE).stream()
                .map(PartnerUser::getPartnerId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<PartnerBackofficeRole> resolveRoles(UUID accountHolderId, UUID partnerId) {
        PartnerUser partnerUser = resolveActivePartnerUser(accountHolderId, partnerId);
        return partnerRoleRepository.findByPartnerUserId(partnerUser.getId()).stream()
                .map(role -> role.getRole())
                .collect(Collectors.toSet());
    }

    private PartnerUser resolveActivePartnerUser(UUID accountHolderId, UUID partnerId) {
        accountHolderService.getById(accountHolderId);
        PartnerUser partnerUser = partnerUserRepository.findByPartnerIdAndAccountHolderId(partnerId, accountHolderId)
                .orElseThrow(() -> new UnauthorizedException("Partner access denied"));
        if (partnerUser.getStatus() != PartnerUserStatus.ACTIVE) {
            throw new UnauthorizedException("Partner user is disabled");
        }
        return partnerUser;
    }
}
