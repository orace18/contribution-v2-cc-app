package com.ccapp.mlm.graph;

import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.mlm.repository.projection.MlmHierarchyMemberProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MlmGraphService {

    private static final String GRAPH_UPLINE_QUERY = """
            MATCH p = (u:User {id: $userId})-[:SPONSORED_BY*1..$depth]->(ancestor:User)
            RETURN ancestor.id AS memberId, min(length(p)) AS level
            ORDER BY level ASC, memberId ASC
            """;

    private static final String GRAPH_DOWNLINE_QUERY = """
            MATCH p = (u:User {id: $userId})<-[:SPONSORED_BY*1..$maxDepth]-(member:User)
            RETURN member.id AS memberId, min(length(p)) AS level
            ORDER BY level ASC, memberId ASC
            LIMIT $maxMembers
            """;

    private static final String GRAPH_LEVEL_QUERY = """
            MATCH p = (u:User {id: $userId})<-[:SPONSORED_BY*1..$level]-(member:User)
            WHERE length(p) = $level
            RETURN member.id AS memberId, $level AS level
            ORDER BY memberId ASC
            """;

    private static final String GRAPH_COUNT_DOWNLINE_QUERY = """
            MATCH (u:User {id: $userId})<-[:SPONSORED_BY*1..]-(member:User)
            RETURN count(distinct member) AS total
            """;

    private static final String GRAPH_DIRECT_PLACEMENT_CHILDREN = """
            MATCH (u:User {id: $userId})<-[:PLACED_UNDER]-(child:User)
            RETURN child.id AS childId
            ORDER BY childId ASC
            """;

    private static final String GRAPH_BRANCH_MEMBER_IDS = """
            MATCH (child:User {id: $childId})<-[:PLACED_UNDER*0..]-(member:User)
            RETURN collect(distinct member.id) AS memberIds
            """;

    private static final String GRAPH_TREE_SUMMARY = """
            MATCH p = (u:User {id: $userId})<-[:PLACED_UNDER*1..]-(member:User)
            RETURN coalesce(max(length(p)), 0) AS maxDepth, count(distinct member) AS totalMembers
            """;

    private final MlmGraphNeo4jGateway graphGateway;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;

    @Transactional(readOnly = true)
    public List<GraphMember> getUpline(UUID userId, int depth) {
        if (userId == null || depth <= 0) {
            return List.of();
        }
        if (graphGateway.isAvailable()) {
            List<Map<String, Object>> rows = graphGateway.read(
                    GRAPH_UPLINE_QUERY,
                    Map.of("userId", userId.toString(), "depth", depth)
            );
            return toGraphMembers(rows);
        }
        return mlmRelationshipRepository.findUpline(userId).stream()
                .map(this::fromProjection)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GraphMember> getDownline(UUID userId, int maxDepth, long maxMembers) {
        if (userId == null) {
            return List.of();
        }
        if (graphGateway.isAvailable()) {
            int depth = maxDepth <= 0 ? Integer.MAX_VALUE : maxDepth;
            long membersLimit = maxMembers <= 0 ? Long.MAX_VALUE : maxMembers;
            List<Map<String, Object>> rows = graphGateway.read(
                    GRAPH_DOWNLINE_QUERY,
                    Map.of(
                            "userId", userId.toString(),
                            "maxDepth", depth,
                            "maxMembers", membersLimit
                    )
            );
            return toGraphMembers(rows);
        }
        return mlmRelationshipRepository.findDownline(userId).stream()
                .map(this::fromProjection)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GraphMember> getLevelMembers(UUID userId, int level) {
        if (userId == null || level < 1) {
            return List.of();
        }
        if (graphGateway.isAvailable()) {
            List<Map<String, Object>> rows = graphGateway.read(
                    GRAPH_LEVEL_QUERY,
                    Map.of("userId", userId.toString(), "level", level)
            );
            return toGraphMembers(rows);
        }
        return mlmRelationshipRepository.findMembersAtLevel(userId, level).stream()
                .map(this::fromProjection)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countDownline(UUID userId) {
        if (userId == null) {
            return 0L;
        }
        if (graphGateway.isAvailable()) {
            List<Map<String, Object>> rows = graphGateway.read(
                    GRAPH_COUNT_DOWNLINE_QUERY,
                    Map.of("userId", userId.toString())
            );
            if (rows.isEmpty()) {
                return 0L;
            }
            Object total = rows.getFirst().get("total");
            if (total instanceof Number number) {
                return number.longValue();
            }
            return 0L;
        }
        return mlmRelationshipRepository.countTeamSize(userId);
    }

    @Transactional(readOnly = true)
    public GraphBinaryTree computeBinaryTree(UUID userId) {
        if (userId == null) {
            return new GraphBinaryTree(null, 0, 0, Map.of());
        }
        if (!graphGateway.isAvailable()) {
            List<UUID> directChildren = mlmRelationshipRepository.findDirectChildrenOrdered(userId);
            Map<UUID, Long> branchSizes = new HashMap<>();
            for (UUID childId : directChildren) {
                branchSizes.put(childId, mlmRelationshipRepository.countTeamSize(childId) + 1);
            }
            return new GraphBinaryTree(userId, 0, mlmRelationshipRepository.countTeamSize(userId), branchSizes);
        }

        List<UUID> directChildren = readDirectPlacementChildren(userId);
        Map<UUID, Long> branchSizes = new HashMap<>();
        int maxDepth = 0;
        long totalMembers = 0L;

        for (UUID childId : directChildren) {
            Set<UUID> branchMembers = readBranchMemberIds(childId);
            branchSizes.put(childId, (long) branchMembers.size());
            totalMembers += branchMembers.size();
        }

        List<Map<String, Object>> summaryRows = graphGateway.read(
                GRAPH_TREE_SUMMARY,
                Map.of("userId", userId.toString())
        );
        if (!summaryRows.isEmpty()) {
            Object depthValue = summaryRows.getFirst().get("maxDepth");
            Object totalValue = summaryRows.getFirst().get("totalMembers");
            if (depthValue instanceof Number number) {
                maxDepth = number.intValue();
            }
            if (totalValue instanceof Number number) {
                totalMembers = number.longValue();
            }
        }

        return new GraphBinaryTree(userId, maxDepth, totalMembers, branchSizes);
    }

    @Transactional(readOnly = true)
    public GraphVolumeSnapshot computeVolumes(UUID userId) {
        if (userId == null) {
            return new GraphVolumeSnapshot(null, BigDecimal.ZERO, Map.of());
        }
        if (!graphGateway.isAvailable()) {
            return new GraphVolumeSnapshot(
                    userId,
                    safeAmount(mlmVolumeLedgerRepository.sumVolumeByAccountHolderId(userId)),
                    Map.of()
            );
        }

        List<UUID> directChildren = readDirectPlacementChildren(userId);
        Map<UUID, BigDecimal> branchVolumes = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (UUID childId : directChildren) {
            Set<UUID> memberIds = readBranchMemberIds(childId);
            if (memberIds.isEmpty()) {
                branchVolumes.put(childId, BigDecimal.ZERO);
                continue;
            }
            BigDecimal branchVolume = safeAmount(
                    mlmVolumeLedgerRepository.sumVolumeByAccountHolderIdAndSourceMemberIdIn(userId, memberIds)
            );
            branchVolumes.put(childId, branchVolume);
            total = total.add(branchVolume);
        }

        return new GraphVolumeSnapshot(userId, total, branchVolumes);
    }

    private List<GraphMember> toGraphMembers(List<Map<String, Object>> rows) {
        List<GraphMember> members = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            UUID memberId = asUuid(row.get("memberId"));
            if (memberId == null) {
                continue;
            }
            int level = 0;
            Object rawLevel = row.get("level");
            if (rawLevel instanceof Number number) {
                level = number.intValue();
            }
            OffsetDateTime linkedAt = row.get("linkedAt") instanceof OffsetDateTime odt ? odt : null;
            members.add(new GraphMember(memberId, level, linkedAt));
        }
        return members;
    }

    private GraphMember fromProjection(MlmHierarchyMemberProjection projection) {
        // getLinkedAt() returns a String (cast(created_at as varchar) in native query)
        // to avoid H2 vs PostgreSQL JDBC type discrepancy. Parse flexibly.
        OffsetDateTime linkedAt = parseLinkedAt(projection.getLinkedAt());
        return new GraphMember(
                UUID.fromString(projection.getMemberId()),
                projection.getLevel() == null ? 0 : projection.getLevel(),
                linkedAt
        );
    }

    /**
     * Parses a timestamp String produced by {@code cast(timestamptz as varchar)}.
     * Handles OffsetDateTime.toString(), Instant.toString(), and H2's space-separated format.
     */
    private OffsetDateTime parseLinkedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalised = raw.trim().replace(" ", "T");
        try {
            return OffsetDateTime.parse(normalised);
        } catch (DateTimeParseException ignored) {
            // Fall through
        }
        try {
            return Instant.parse(normalised).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private List<UUID> readDirectPlacementChildren(UUID userId) {
        List<Map<String, Object>> rows = graphGateway.read(
                GRAPH_DIRECT_PLACEMENT_CHILDREN,
                Map.of("userId", userId.toString())
        );
        List<UUID> childIds = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            UUID childId = asUuid(row.get("childId"));
            if (childId != null) {
                childIds.add(childId);
            }
        }
        return childIds;
    }

    private Set<UUID> readBranchMemberIds(UUID childId) {
        List<Map<String, Object>> rows = graphGateway.read(
                GRAPH_BRANCH_MEMBER_IDS,
                Map.of("childId", childId.toString())
        );
        if (rows.isEmpty()) {
            return Set.of();
        }
        Object rawMemberIds = rows.getFirst().get("memberIds");
        if (!(rawMemberIds instanceof List<?> values)) {
            return Set.of();
        }
        Set<UUID> memberIds = new HashSet<>();
        for (Object value : values) {
            UUID memberId = asUuid(value);
            if (memberId != null) {
                memberIds.add(memberId);
            }
        }
        return memberIds;
    }

    private UUID asUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String raw && !raw.isBlank()) {
            try {
                return UUID.fromString(raw.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public record GraphMember(
            UUID memberId,
            int level,
            OffsetDateTime linkedAt
    ) {
    }

    public record GraphBinaryTree(
            UUID rootUserId,
            int maxDepth,
            long totalMembers,
            Map<UUID, Long> branchSizes
    ) {
    }

    public record GraphVolumeSnapshot(
            UUID rootUserId,
            BigDecimal totalVolume,
            Map<UUID, BigDecimal> branchVolumes
    ) {
    }
}

