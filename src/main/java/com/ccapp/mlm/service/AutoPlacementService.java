package com.ccapp.mlm.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoPlacementService {

    private static final int MAX_DIRECT_CHILDREN = 5;

    private final MlmRelationshipRepository mlmRelationshipRepository;

    @Value("${mlm.query.max-safe-depth:64}")
    private int maxSafeDepth;

    public UUID resolvePlacementParent(UUID sponsorAccountHolderId) {
        List<UUID> directChildren = mlmRelationshipRepository.findDirectChildrenOrdered(sponsorAccountHolderId);
        if (directChildren.size() < MAX_DIRECT_CHILDREN) {
            return sponsorAccountHolderId;
        }

        Queue<Node> queue = new ArrayDeque<>();
        for (UUID childId : directChildren) {
            queue.add(new Node(childId, 1));
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.depth() > maxSafeDepth) {
                throw new BusinessException("MLM placement exceeded safe depth threshold");
            }

            List<UUID> children = mlmRelationshipRepository.findDirectChildrenOrdered(current.memberId());
            if (children.size() < MAX_DIRECT_CHILDREN) {
                return current.memberId();
            }

            for (UUID childId : children) {
                queue.add(new Node(childId, current.depth() + 1));
            }
        }

        throw new BusinessException("Sponsor has no available placement position");
    }

    private record Node(UUID memberId, int depth) {
    }
}
