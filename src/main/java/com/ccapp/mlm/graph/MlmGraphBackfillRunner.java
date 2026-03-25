package com.ccapp.mlm.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mlm.graph.backfill", name = "enabled", havingValue = "true")
@Slf4j
public class MlmGraphBackfillRunner implements ApplicationRunner {

    private final MlmGraphSyncService mlmGraphSyncService;
    private final MlmGraphProperties mlmGraphProperties;

    @Override
    public void run(ApplicationArguments args) {
        MlmGraphSyncService.GraphSyncReport report = mlmGraphSyncService.rebuildFromSourceOfTruth(
                mlmGraphProperties.getBackfill().isPruneOrphans()
        );

        log.info("mlm_graph_backfill_completed postgresUsers={} graphUsers={} missingGraphNodes={} orphanGraphNodes={} inconsistentLinks={} cycleCount={} maxDepth={}",
                report.postgresUsers(),
                report.graphUsers(),
                report.missingGraphNodes(),
                report.orphanGraphNodes(),
                report.inconsistentLinks(),
                report.cycleCount(),
                report.maxPlacementDepth());

        if (mlmGraphProperties.getBackfill().isFailFast()
                && (report.missingGraphNodes() > 0
                || report.orphanGraphNodes() > 0
                || report.inconsistentLinks() > 0
                || report.cycleCount() > 0)) {
            throw new IllegalStateException("MLM graph backfill validation failed");
        }
    }
}

