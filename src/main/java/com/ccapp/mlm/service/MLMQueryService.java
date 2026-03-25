package com.ccapp.mlm.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mlm.graph.MlmGraphService;
import com.ccapp.mlm.dto.MlmHierarchyMemberResponse;
import com.ccapp.mlm.dto.MlmTeamSizeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MLMQueryService {

    private final MlmGraphService mlmGraphService;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountHolderService accountHolderService;
    private final AccessControlService accessControlService;

    @Value("${mlm.query.max-level:20}")
    private int maxQueryableLevel;

    @Value("${mlm.query.max-downline-members:5000}")
    private long maxDownlineMembers;

    @Transactional(readOnly = true)
    public List<MlmHierarchyMemberResponse> getUpline(UUID actorUserId, UUID accountHolderId) {
        assertAccess(actorUserId, accountHolderId);
        return toResponse(mlmGraphService.getUpline(accountHolderId, maxQueryableLevel));
    }

    @Transactional(readOnly = true)
    public List<MlmHierarchyMemberResponse> getDownline(UUID actorUserId, UUID accountHolderId) {
        assertAccess(actorUserId, accountHolderId);
        long teamSize = mlmGraphService.countDownline(accountHolderId);
        if (teamSize > maxDownlineMembers) {
            throw new BusinessException("Downline query blocked by performance guard. Use level queries instead.");
        }
        return toResponse(mlmGraphService.getDownline(accountHolderId, maxQueryableLevel, maxDownlineMembers));
    }

    @Transactional(readOnly = true)
    public MlmTeamSizeResponse getTeamSize(UUID actorUserId, UUID accountHolderId) {
        assertAccess(actorUserId, accountHolderId);
        return new MlmTeamSizeResponse(accountHolderId, mlmGraphService.countDownline(accountHolderId));
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
        return toResponse(mlmGraphService.getLevelMembers(accountHolderId, level));
    }

    private void assertAccess(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
    }

    private List<MlmHierarchyMemberResponse> toResponse(List<MlmGraphService.GraphMember> members) {
        List<UUID> memberIds = members.stream().map(MlmGraphService.GraphMember::memberId).toList();
        Map<UUID, AccountHolder> accountHolderById = new HashMap<>();
        accountHolderRepository.findAllById(memberIds)
                .forEach(accountHolder -> accountHolderById.put(accountHolder.getId(), accountHolder));

        return members.stream()
                .map(member -> {
                    AccountHolder accountHolder = accountHolderById.get(member.memberId());
                    return new MlmHierarchyMemberResponse(
                            member.memberId(),
                            member.level(),
                            accountHolder == null ? null : accountHolder.getFirstName(),
                            accountHolder == null ? null : accountHolder.getLastName(),
                            member.linkedAt()
                    );
                })
                .toList();
    }
}
