package com.ccapp.mlm.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mlm.graph.sync.reconcile", name = "enabled", havingValue = "true")
@Slf4j
public class MlmGraphConsistencyJob {

    private final MlmGraphSyncService mlmGraphSyncService;

    @Scheduled(cron = "${mlm.graph.sync.reconcile.cron:0 */10 * * * *}")
    public void reconcileGraph() {
        try {
            MlmGraphSyncService.GraphSyncReport report = mlmGraphSyncService.reconcileFromSourceOfTruth(true);
            log.info("mlm_graph_reconcile_completed postgresUsers={} graphUsers={} missingGraphNodes={} orphanGraphNodes={} inconsistentLinks={} cycleCount={} maxDepth={}",
                    report.postgresUsers(),
                    report.graphUsers(),
                    report.missingGraphNodes(),
                    report.orphanGraphNodes(),
                    report.inconsistentLinks(),
                    report.cycleCount(),
                    report.maxPlacementDepth());
        } catch (RuntimeException ex) {
            log.error("mlm_graph_reconcile_failed message={}", ex.getMessage(), ex);
        }
    }
}

