package com.ccapp.mlm.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.dto.MlmHierarchyMemberResponse;
import com.ccapp.mlm.dto.MlmTeamSizeResponse;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.projection.MlmHierarchyMemberProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MLMQueryService {

    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;

    @Value("${mlm.query.max-level:20}")
    private int maxQueryableLevel;

    @Value("${mlm.query.max-downline-members:5000}")
    private long maxDownlineMembers;

    @Transactional(readOnly = true)
    public List<MlmHierarchyMemberResponse> getUpline(UUID actorUserId, UUID accountHolderId) {
        assertAccess(actorUserId, accountHolderId);
        return mlmRelationshipRepository.findUpline(accountHolderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MlmHierarchyMemberResponse> getDownline(UUID actorUserId, UUID accountHolderId) {
        assertAccess(actorUserId, accountHolderId);
        long teamSize = mlmRelationshipRepository.countTeamSize(accountHolderId);
        if (teamSize > maxDownlineMembers) {
            throw new BusinessException("Downline query blocked by performance guard. Use level queries instead.");
        }
        return mlmRelationshipRepository.findDownline(accountHolderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MlmTeamSizeResponse getTeamSize(UUID actorUserId, UUID accountHolderId) {
        assertAccess(actorUserId, accountHolderId);
        return new MlmTeamSizeResponse(accountHolderId, mlmRelationshipRepository.countTeamSize(accountHolderId));
    }

    @Transactional(readOnly = true)
    public List<MlmHierarchyMemberResponse> getLevelMembers(UUID actorUserId, UUID accountHolderId, int level) {
        if (level < 1) {
            throw new BusinessException("Level must be >= 1");
        }
        if (level > maxQueryableLevel) {
            throw new BusinessException("Level exceeds configured query guard");
        }
        assertAccess(actorUserId, accountHolderId);
        return mlmRelationshipRepository.findMembersAtLevel(accountHolderId, level).stream()
                .map(this::toResponse)
                .toList();
    }

    private void assertAccess(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
    }

    private MlmHierarchyMemberResponse toResponse(MlmHierarchyMemberProjection projection) {
        return new MlmHierarchyMemberResponse(
                UUID.fromString(projection.getMemberId()),
                projection.getLevel(),
                projection.getFirstName(),
                projection.getLastName(),
                projection.getLinkedAt()
        );
    }
}
