package com.ccapp.mlm.graph;

import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlmGraphSyncService {

    private static final String UPSERT_USER_NODE = """
            MERGE (u:User {id: $userId})
            ON CREATE SET u.createdAt = datetime()
            SET u.updatedAt = datetime()
            RETURN u.id AS userId
            """;

    private static final String UPSERT_SPONSOR_PLACEMENT = """
            MERGE (u:User {id: $userId})
            ON CREATE SET u.createdAt = datetime()
            SET u.updatedAt = datetime()
            WITH u
            OPTIONAL MATCH (u)-[oldSponsor:SPONSORED_BY]->()
            DELETE oldSponsor
            WITH u
            OPTIONAL MATCH (u)-[oldPlacement:PLACED_UNDER]->()
            DELETE oldPlacement
            WITH u
            FOREACH (_ IN CASE WHEN $sponsorId IS NULL THEN [] ELSE [1] END |
                MERGE (s:User {id: $sponsorId})
                ON CREATE SET s.createdAt = datetime()
                SET s.updatedAt = datetime()
                MERGE (u)-[r1:SPONSORED_BY]->(s)
                ON CREATE SET r1.createdAt = datetime()
                SET r1.updatedAt = datetime()
            )
            WITH u
            MERGE (p:User {id: $placementId})
            ON CREATE SET p.createdAt = datetime()
            SET p.updatedAt = datetime()
            MERGE (u)-[r2:PLACED_UNDER]->(p)
            ON CREATE SET r2.createdAt = datetime()
            SET r2.updatedAt = datetime()
            RETURN u.id AS userId
            """;

    private static final String LIST_GRAPH_NODE_IDS = """
            MATCH (u:User)
            RETURN u.id AS userId
            """;

    private static final String DELETE_ORPHAN_NODES = """
            MATCH (u:User)
            WHERE NOT u.id IN $validUserIds
            DETACH DELETE u
            RETURN count(u) AS deleted
            """;

    private static final String COUNT_CYCLES = """
            MATCH p = (u:User)-[:PLACED_UNDER*1..]->(u)
            RETURN count(p) AS cycles
            """;

    private static final String MAX_PLACEMENT_DEPTH = """
            MATCH p = (root:User)<-[:PLACED_UNDER*1..]-(member:User)
            RETURN coalesce(max(length(p)), 0) AS maxDepth
            """;

    private static final String SNAPSHOT_RELATIONSHIPS = """
            MATCH (u:User)
            OPTIONAL MATCH (u)-[:SPONSORED_BY]->(s:User)
            OPTIONAL MATCH (u)-[:PLACED_UNDER]->(p:User)
            RETURN u.id AS userId, s.id AS sponsorId, p.id AS placementId
            """;

    private final MlmGraphNeo4jGateway graphGateway;
    private final MlmGraphProperties graphProperties;
    private final AccountHolderRepository accountHolderRepository;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;

    public void syncUserCreation(UUID userId) {
        if (userId == null || !graphGateway.isAvailable()) {
            return;
        }
        executeWithRetry("sync_user_creation", userId, false, () ->
                graphGateway.write(UPSERT_USER_NODE, Map.of("userId", userId.toString()))
        );
    }

    public void syncPlacementAssignment(UUID userId, UUID sponsorId, UUID placementId) {
        if (userId == null || !graphGateway.isAvailable()) {
            return;
        }
        UUID normalizedPlacementId = placementId == null ? userId : placementId;
        executeWithRetry("sync_placement_assignment", userId, false, () ->
                graphGateway.write(
                        UPSERT_SPONSOR_PLACEMENT,
                        Map.of(
                                "userId", userId.toString(),
                                "sponsorId", sponsorId == null ? null : sponsorId.toString(),
                                "placementId", normalizedPlacementId.toString()
                        )
                )
        );
    }

    public void syncUserFromSourceOfTruth(UUID userId) {
        if (userId == null || !graphGateway.isAvailable()) {
            return;
        }
        if (!accountHolderRepository.existsById(userId)) {
            return;
        }

        MlmMemberLink link = mlmMemberLinkRepository.findById(userId).orElse(null);
        UUID sponsorId = link == null ? null : link.getSponsorAccountHolderId();
        UUID placementId = link == null || link.getPlacementAccountHolderId() == null
                ? userId
                : link.getPlacementAccountHolderId();
        syncPlacementAssignment(userId, sponsorId, placementId);
    }

    public GraphSyncReport reconcileFromSourceOfTruth(boolean pruneOrphans) {
        return performSync(false, pruneOrphans, false);
    }

    public GraphSyncReport rebuildFromSourceOfTruth(boolean pruneOrphans) {
        return performSync(true, pruneOrphans, true);
    }

    public boolean isNetworkReady(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (!graphGateway.isAvailable()) {
            return true;
        }
        GraphLinkSnapshot snapshot = readUserGraphLinkSnapshot(userId);
        if (snapshot == null || snapshot.placementId() == null) {
            return false;
        }
        MlmMemberLink link = mlmMemberLinkRepository.findById(userId).orElse(null);
        if (link == null) {
            return snapshot.placementId().equals(userId);
        }
        if (!Objects.equals(link.getPlacementAccountHolderId(), snapshot.placementId())) {
            return false;
        }
        return Objects.equals(link.getSponsorAccountHolderId(), snapshot.sponsorId());
    }

    private GraphSyncReport performSync(boolean wipeExistingGraph, boolean pruneOrphans, boolean failFast) {
        if (!graphGateway.isAvailable()) {
            return new GraphSyncReport(0, 0, 0, 0, 0, 0, 0);
        }

        List<UUID> postgresUserIds = accountHolderRepository.findAllIds();
        if (wipeExistingGraph) {
            executeWithRetry("wipe_graph", null, failFast, () ->
                    graphGateway.write("MATCH (u:User) DETACH DELETE u", Map.of())
            );
        }

        for (UUID userId : postgresUserIds) {
            syncUserCreation(userId);
        }

        Map<UUID, MlmMemberLink> linksByUserId = new HashMap<>();
        for (MlmMemberLink link : mlmMemberLinkRepository.findAll()) {
            linksByUserId.put(link.getAccountHolderId(), link);
        }

        for (UUID userId : postgresUserIds) {
            MlmMemberLink link = linksByUserId.get(userId);
            UUID sponsorId = link == null ? null : link.getSponsorAccountHolderId();
            UUID placementId = link == null || link.getPlacementAccountHolderId() == null
                    ? userId
                    : link.getPlacementAccountHolderId();
            syncPlacementAssignment(userId, sponsorId, placementId);
        }

        long orphanCount = 0L;
        if (pruneOrphans) {
            orphanCount = pruneGraphOrphans(postgresUserIds);
        }

        Set<UUID> graphUserIds = listGraphUserIds();
        Set<UUID> missingGraphNodes = new HashSet<>(postgresUserIds);
        missingGraphNodes.removeAll(graphUserIds);

        Set<UUID> orphanGraphNodes = new HashSet<>(graphUserIds);
        orphanGraphNodes.removeAll(new HashSet<>(postgresUserIds));

        long inconsistentLinks = countInconsistentLinks(linksByUserId);
        long cycleCount = countPlacementCycles();
        int maxDepth = maxPlacementDepth();

        return new GraphSyncReport(
                postgresUserIds.size(),
                graphUserIds.size(),
                missingGraphNodes.size(),
                Math.max(orphanGraphNodes.size(), orphanCount),
                inconsistentLinks,
                cycleCount,
                maxDepth
        );
    }

    private long pruneGraphOrphans(List<UUID> postgresUserIds) {
        List<String> validUserIds = postgresUserIds.stream().map(UUID::toString).toList();
        List<Map<String, Object>> rows = executeWithRetry("prune_graph_orphans", null, false, () ->
                graphGateway.write(DELETE_ORPHAN_NODES, Map.of("validUserIds", validUserIds))
        );
        if (rows.isEmpty()) {
            return 0L;
        }
        return asLong(rows.getFirst().get("deleted"));
    }

    private Set<UUID> listGraphUserIds() {
        List<Map<String, Object>> rows = executeWithRetry("list_graph_nodes", null, false, () ->
                graphGateway.read(LIST_GRAPH_NODE_IDS, Map.of())
        );
        Set<UUID> ids = new HashSet<>();
        for (Map<String, Object> row : rows) {
            UUID userId = asUuid(row.get("userId"));
            if (userId != null) {
                ids.add(userId);
            }
        }
        return ids;
    }

    private Map<UUID, GraphLinkSnapshot> readGraphRelationshipSnapshot() {
        List<Map<String, Object>> rows = executeWithRetry("read_graph_relationship_snapshot", null, false, () ->
                graphGateway.read(SNAPSHOT_RELATIONSHIPS, Map.of())
        );
        Map<UUID, GraphLinkSnapshot> byUserId = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID userId = asUuid(row.get("userId"));
            if (userId == null) {
                continue;
            }
            byUserId.put(userId, new GraphLinkSnapshot(
                    userId,
                    asUuid(row.get("sponsorId")),
                    asUuid(row.get("placementId"))
            ));
        }
        return byUserId;
    }

    private GraphLinkSnapshot readUserGraphLinkSnapshot(UUID targetUserId) {
        if (targetUserId == null) return null;
        String query = """
                MATCH (u:User {id: $userId})
                OPTIONAL MATCH (u)-[:SPONSORED_BY]->(s:User)
                OPTIONAL MATCH (u)-[:PLACED_UNDER]->(p:User)
                RETURN u.id AS userId, s.id AS sponsorId, p.id AS placementId
                """;
        List<Map<String, Object>> rows = executeWithRetry("read_user_graph_relationship", targetUserId, false, () ->
                graphGateway.read(query, Map.of("userId", targetUserId.toString()))
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        UUID userId = asUuid(row.get("userId"));
        if (userId == null) {
            return null;
        }
        return new GraphLinkSnapshot(
                userId,
                asUuid(row.get("sponsorId")),
                asUuid(row.get("placementId"))
        );
    }

    private long countInconsistentLinks(Map<UUID, MlmMemberLink> linksByUserId) {
        Map<UUID, GraphLinkSnapshot> graphSnapshot = readGraphRelationshipSnapshot();
        long inconsistent = 0L;
        for (Map.Entry<UUID, GraphLinkSnapshot> entry : graphSnapshot.entrySet()) {
            UUID userId = entry.getKey();
            GraphLinkSnapshot graph = entry.getValue();
            MlmMemberLink pg = linksByUserId.get(userId);
            UUID expectedPlacementId = pg == null || pg.getPlacementAccountHolderId() == null ? userId : pg.getPlacementAccountHolderId();
            UUID expectedSponsorId = pg == null ? null : pg.getSponsorAccountHolderId();
            if (!Objects.equals(expectedPlacementId, graph.placementId())
                    || !Objects.equals(expectedSponsorId, graph.sponsorId())) {
                inconsistent++;
            }
        }
        return inconsistent;
    }

    private long countPlacementCycles() {
        List<Map<String, Object>> rows = executeWithRetry("count_cycles", null, false, () ->
                graphGateway.read(COUNT_CYCLES, Map.of())
        );
        if (rows.isEmpty()) {
            return 0L;
        }
        return asLong(rows.getFirst().get("cycles"));
    }

    private int maxPlacementDepth() {
        List<Map<String, Object>> rows = executeWithRetry("max_placement_depth", null, false, () ->
                graphGateway.read(MAX_PLACEMENT_DEPTH, Map.of())
        );
        if (rows.isEmpty()) {
            return 0;
        }
        Object value = rows.getFirst().get("maxDepth");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private List<Map<String, Object>> executeWithRetry(String operation,
                                                       UUID userId,
                                                       boolean failFast,
                                                       java.util.function.Supplier<List<Map<String, Object>>> work) {
        int maxAttempts = Math.max(1, graphProperties.getSync().getMaxRetries());
        long retryDelayMs = Math.max(0L, graphProperties.getSync().getRetryDelayMs());
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return work.get();
            } catch (Neo4jException ex) {
                lastError = ex;
                log.warn("mlm_graph_sync_retry operation={} userId={} attempt={}/{} message={}",
                        operation, userId, attempt, maxAttempts, ex.getMessage());
                if (attempt == maxAttempts) {
                    break;
                }
                sleepQuietly(retryDelayMs);
            } catch (RuntimeException ex) {
                lastError = ex;
                break;
            }
        }

        if (lastError != null && failFast) {
            throw lastError;
        }
        if (lastError != null) {
            log.error("mlm_graph_sync_failed operation={} userId={} message={}",
                    operation, userId, lastError.getMessage(), lastError);
        }
        return List.of();
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
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

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record GraphLinkSnapshot(
            UUID userId,
            UUID sponsorId,
            UUID placementId
    ) {
    }

    public record GraphSyncReport(
            long postgresUsers,
            long graphUsers,
            long missingGraphNodes,
            long orphanGraphNodes,
            long inconsistentLinks,
            long cycleCount,
            int maxPlacementDepth
    ) {
    }
}
