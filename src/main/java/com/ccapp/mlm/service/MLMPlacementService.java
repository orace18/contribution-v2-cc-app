package com.ccapp.mlm.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.dto.MlmPlacementResponse;
import com.ccapp.mlm.event.MlmPlacementCreatedEvent;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MLMPlacementService {

    private static final int MAX_DIRECT_CHILDREN = 5;

    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;
    private final AutoPlacementService autoPlacementService;
    private final MlmPlacementAuditService mlmPlacementAuditService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public MlmPlacementResponse placeAccountHolder(UUID actorUserId,
                                                   UUID accountHolderId,
                                                   UUID sponsorAccountHolderId) {
        if (accountHolderId.equals(sponsorAccountHolderId)) {
            throw new BusinessException("Account holder cannot sponsor itself");
        }
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        accountHolderService.getById(accountHolderId);
        accountHolderService.getById(sponsorAccountHolderId);

        ensureSelfRelationship(sponsorAccountHolderId);
        ensureSelfRelationship(accountHolderId);

        if (hasExistingSponsor(accountHolderId)) {
            throw new ConflictException("Account holder already has a sponsor");
        }

        UUID placementParentId = autoPlacementService.resolvePlacementParent(sponsorAccountHolderId, MAX_DIRECT_CHILDREN);

        List<MlmRelationship> ancestors = mlmRelationshipRepository.findAncestors(placementParentId);
        if (ancestors.isEmpty()) {
            throw new BusinessException("Invalid MLM tree state for placement parent");
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<MlmRelationship> toInsert = new ArrayList<>();
        for (MlmRelationship ancestor : ancestors) {
            boolean relationExists = mlmRelationshipRepository
                    .existsByAncestorIdAndDescendantId(ancestor.getAncestorId(), accountHolderId);
            if (relationExists) {
                continue;
            }
            toInsert.add(MlmRelationship.builder()
                    .ancestorId(ancestor.getAncestorId())
                    .descendantId(accountHolderId)
                    .level(ancestor.getLevel() + 1)
                    .createdAt(now)
                    .build());
        }

        mlmRelationshipRepository.saveAll(toInsert);
        upsertMemberLink(accountHolderId, sponsorAccountHolderId, placementParentId);
        domainEventPublisher.publish(MlmPlacementCreatedEvent.create(
                accountHolderId,
                sponsorAccountHolderId,
                placementParentId
        ));

        long directChildren = mlmRelationshipRepository.countByAncestorIdAndLevel(placementParentId, 1);
        long sponsorTeamSize = mlmRelationshipRepository.countTeamSize(sponsorAccountHolderId);
        PlatformMonitoringHooks.onMlmPlacementCreated();
        mlmPlacementAuditService.record(
                accountHolderId,
                sponsorAccountHolderId,
                placementParentId,
                actorUserId,
                java.util.Map.of(
                        "directChildren", String.valueOf(directChildren),
                        "sponsorTeamSize", String.valueOf(sponsorTeamSize)
                )
        );
        log.info("mlm_placement_created accountHolderId={} sponsorId={} placementParentId={} directChildren={} sponsorTeamSize={}",
                accountHolderId, sponsorAccountHolderId, placementParentId, directChildren, sponsorTeamSize);

        return new MlmPlacementResponse(
                accountHolderId,
                sponsorAccountHolderId,
                placementParentId,
                directChildren,
                sponsorTeamSize,
                now
        );
    }

    private void ensureSelfRelationship(UUID accountHolderId) {
        if (mlmRelationshipRepository.existsByAncestorIdAndDescendantId(accountHolderId, accountHolderId)) {
            ensureRootMemberLink(accountHolderId);
            return;
        }
        mlmRelationshipRepository.save(MlmRelationship.builder()
                .ancestorId(accountHolderId)
                .descendantId(accountHolderId)
                .level(0)
                .createdAt(OffsetDateTime.now())
                .build());
        ensureRootMemberLink(accountHolderId);
    }

    private void ensureRootMemberLink(UUID accountHolderId) {
        if (mlmMemberLinkRepository.existsById(accountHolderId)) {
            return;
        }
        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(accountHolderId)
                .sponsorAccountHolderId(null)
                .placementAccountHolderId(accountHolderId)
                .build());
    }

    private boolean hasExistingSponsor(UUID accountHolderId) {
        MlmMemberLink link = mlmMemberLinkRepository.findById(accountHolderId).orElse(null);
        if (link != null && link.getSponsorAccountHolderId() != null) {
            return true;
        }
        return mlmRelationshipRepository.countByDescendantIdAndLevelGreaterThan(accountHolderId, 0) > 0;
    }

    private void upsertMemberLink(UUID accountHolderId, UUID sponsorAccountHolderId, UUID placementParentId) {
        MlmMemberLink memberLink = mlmMemberLinkRepository.findById(accountHolderId)
                .orElseGet(() -> MlmMemberLink.builder()
                        .accountHolderId(accountHolderId)
                        .build());
        memberLink.setSponsorAccountHolderId(sponsorAccountHolderId);
        memberLink.setPlacementAccountHolderId(placementParentId);
        mlmMemberLinkRepository.save(memberLink);
    }
}
