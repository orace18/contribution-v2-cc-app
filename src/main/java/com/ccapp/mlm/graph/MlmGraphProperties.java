package com.ccapp.mlm.graph;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mlm.graph")
public class MlmGraphProperties {

    private boolean enabled = false;
    private Neo4j neo4j = new Neo4j();
    private Sync sync = new Sync();
    private Backfill backfill = new Backfill();

    @Getter
    @Setter
    public static class Neo4j {
        private String uri;
        private String username = "neo4j";
        private String password;
        private String database = "neo4j";
    }

    @Getter
    @Setter
    public static class Sync {
        private int maxRetries = 3;
        private long retryDelayMs = 200L;
        private Reconcile reconcile = new Reconcile();
    }

    @Getter
    @Setter
    public static class Reconcile {
        private boolean enabled = false;
        private String cron = "0 */10 * * * *";
    }

    @Getter
    @Setter
    public static class Backfill {
        private boolean enabled = false;
        private boolean pruneOrphans = true;
        private boolean failFast = true;
    }
}

