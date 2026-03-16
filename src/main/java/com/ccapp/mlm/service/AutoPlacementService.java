package com.ccapp.mlm.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoPlacementService {

    private final MlmRelationshipRepository mlmRelationshipRepository;

    @Value("${mlm.autoplacement.max-nodes-scan:50000}")
    private int maxNodesScan;

    @Transactional(readOnly = true)
    public UUID resolvePlacementParent(UUID sponsorAccountHolderId, int maxDirectChildren) {
        if (maxDirectChildren <= 0) {
            throw new BusinessException("Invalid max direct children for MLM placement");
        }

        ArrayDeque<UUID> queue = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        queue.add(sponsorAccountHolderId);
        int scanned = 0;

        while (!queue.isEmpty()) {
            UUID candidateId = queue.poll();
            if (!visited.add(candidateId)) {
                continue;
            }

            List<UUID> directChildren = mlmRelationshipRepository.findDirectChildrenOrdered(candidateId);
            if (directChildren.size() < maxDirectChildren) {
                return candidateId;
            }

            queue.addAll(directChildren);
            scanned++;
            if (scanned > maxNodesScan) {
                throw new BusinessException("Auto placement aborted by safety threshold");
            }
        }

        throw new BusinessException("Sponsor has no available leg");
    }
}
