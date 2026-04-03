package com.ccapp.events.service;

import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.enums.EventAssignmentRole;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.repository.EventAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventAccessService {

    private final EventAssignmentRepository eventAssignmentRepository;

    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new UnauthorizedException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AdminPrincipal adminPrincipal) {
            return new CurrentActor(adminPrincipal.adminUserId(), null, true);
        }
        if (principal instanceof AuthenticatedPrincipal principalUser) {
            UUID accountHolderId = principalUser.accountHolderId() != null
                    ? principalUser.accountHolderId()
                    : principalUser.userId();
            UUID actorId = principalUser.userId() != null
                    ? principalUser.userId()
                    : accountHolderId;
            if (actorId == null) {
                throw new UnauthorizedException("User context missing");
            }
            return new CurrentActor(actorId, accountHolderId, false);
        }
        throw new UnauthorizedException("Authentication required");
    }

    public UUID currentActorId() {
        return currentActor().actorId();
    }

    public UUID currentAccountHolderId() {
        CurrentActor actor = currentActor();
        if (actor.accountHolderId() == null) {
            throw new UnauthorizedException("User account holder context required");
        }
        return actor.accountHolderId();
    }

    public boolean isAdmin() {
        return currentActor().admin();
    }

    public UUID resolveOrganizerAccountHolderId(UUID requestedOrganizerAccountHolderId) {
        CurrentActor actor = currentActor();
        if (actor.admin()) {
            if (requestedOrganizerAccountHolderId == null) {
                throw new BusinessException("organizerAccountHolderId is required for admin actions");
            }
            return requestedOrganizerAccountHolderId;
        }
        if (requestedOrganizerAccountHolderId != null && !requestedOrganizerAccountHolderId.equals(actor.accountHolderId())) {
            throw new UnauthorizedException("Organizer account holder must match the authenticated user");
        }
        return currentAccountHolderId();
    }

    @Transactional(readOnly = true)
    public void assertCanManage(Event event) {
        if (event == null) {
            throw new UnauthorizedException("Event access denied");
        }
        if (isAdmin()) {
            return;
        }
        UUID currentAccountHolderId = currentAccountHolderId();
        if (event.getOrganizerAccountHolderId().equals(currentAccountHolderId)) {
            return;
        }
        if (eventAssignmentRepository.existsByEventIdAndAccountHolderIdAndRoleIn(
                event.getId(),
                currentAccountHolderId,
                List.of(EventAssignmentRole.ORGANIZER)
        )) {
            return;
        }
        throw new UnauthorizedException("Event management access denied");
    }

    @Transactional(readOnly = true)
    public void assertCanView(Event event) {
        if (event == null) {
            throw new UnauthorizedException("Event access denied");
        }
        if (isAdmin() || event.getStatus() == EventStatus.PUBLISHED) {
            return;
        }
        UUID currentAccountHolderId = currentAccountHolderId();
        if (event.getOrganizerAccountHolderId().equals(currentAccountHolderId)) {
            return;
        }
        if (eventAssignmentRepository.existsByEventIdAndAccountHolderIdAndRoleIn(
                event.getId(),
                currentAccountHolderId,
                List.of(EventAssignmentRole.ORGANIZER, EventAssignmentRole.CONTROL_AGENT)
        )) {
            return;
        }
        throw new UnauthorizedException("Event access denied");
    }

    @Transactional(readOnly = true)
    public void assertCanScan(UUID eventId) {
        if (isAdmin()) {
            return;
        }
        UUID currentAccountHolderId = currentAccountHolderId();
        if (eventAssignmentRepository.existsByEventIdAndAccountHolderIdAndRoleIn(
                eventId,
                currentAccountHolderId,
                List.of(EventAssignmentRole.ORGANIZER, EventAssignmentRole.CONTROL_AGENT)
        )) {
            return;
        }
        throw new UnauthorizedException("Scanner access denied");
    }

    public record CurrentActor(UUID actorId, UUID accountHolderId, boolean admin) {
    }
}
