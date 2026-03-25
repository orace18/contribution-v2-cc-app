package com.ccapp.mlm.graph;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class MlmGraphNeo4jGateway {

    private final MlmGraphProperties properties;

    private Driver driver;

    @PostConstruct
    void init() {
        if (!properties.isEnabled()) {
            log.info("mlm_graph_gateway_disabled reason=feature_flag");
            return;
        }

        String uri = normalize(properties.getNeo4j().getUri());
        if (uri == null) {
            log.warn("mlm_graph_gateway_disabled reason=missing_uri");
            return;
        }

        try {
            AuthToken authToken = resolveAuthToken();
            this.driver = GraphDatabase.driver(
                    uri,
                    authToken,
                    Config.builder()
                            .withConnectionTimeout(5, TimeUnit.SECONDS)
                            .withMaxConnectionLifetime(10, TimeUnit.MINUTES)
                            .build()
            );
            this.driver.verifyConnectivity();
            log.info("mlm_graph_gateway_connected uri={} database={}", uri, normalize(properties.getNeo4j().getDatabase()));
        } catch (Exception ex) {
            log.error("mlm_graph_gateway_connection_failed uri={} message={}", uri, ex.getMessage(), ex);
            closeDriver();
        }
    }

    @PreDestroy
    void shutdown() {
        closeDriver();
    }

    public boolean isAvailable() {
        return driver != null;
    }

    public List<Map<String, Object>> read(String cypher, Map<String, Object> params) {
        if (!isAvailable()) {
            return List.of();
        }
        return execute(cypher, params, false);
    }

    public List<Map<String, Object>> write(String cypher, Map<String, Object> params) {
        if (!isAvailable()) {
            return List.of();
        }
        return execute(cypher, params, true);
    }

    private List<Map<String, Object>> execute(String cypher, Map<String, Object> params, boolean write) {
        SessionConfig config = sessionConfig();
        try (Session session = config == null ? driver.session() : driver.session(config)) {
            if (write) {
                return session.executeWrite(tx -> runQuery(tx, cypher, params));
            }
            return session.executeRead(tx -> runQuery(tx, cypher, params));
        } catch (SessionExpiredException | ServiceUnavailableException ex) {
            throw ex;
        } catch (Neo4jException ex) {
            throw ex;
        }
    }

    private List<Map<String, Object>> runQuery(TransactionContext tx, String cypher, Map<String, Object> params) {
        Result result = tx.run(cypher, params == null ? Map.of() : params);
        List<Map<String, Object>> rows = new ArrayList<>();
        while (result.hasNext()) {
            rows.add(toMap(result.next()));
        }
        return rows;
    }

    private Map<String, Object> toMap(Record record) {
        Map<String, Object> row = new HashMap<>();
        for (String key : record.keys()) {
            row.put(key, convertValue(record.get(key)));
        }
        return row;
    }

    private Object convertValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        Object raw = value.asObject();
        if (raw instanceof ZonedDateTime zdt) {
            return zdt.toOffsetDateTime();
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt.atOffset(ZoneOffset.UTC);
        }
        if (raw instanceof List<?> rawList) {
            List<Object> converted = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                if (item instanceof ZonedDateTime zdt) {
                    converted.add(zdt.toOffsetDateTime());
                } else if (item instanceof LocalDateTime ldt) {
                    converted.add(ldt.atOffset(ZoneOffset.UTC));
                } else {
                    converted.add(item);
                }
            }
            return converted;
        }
        return raw;
    }

    private SessionConfig sessionConfig() {
        String database = normalize(properties.getNeo4j().getDatabase());
        if (database == null) {
            return null;
        }
        return SessionConfig.forDatabase(database);
    }

    private AuthToken resolveAuthToken() {
        String username = normalize(properties.getNeo4j().getUsername());
        String password = properties.getNeo4j().getPassword();
        if (username == null || password == null || password.isBlank()) {
            return AuthTokens.none();
        }
        return AuthTokens.basic(username, password);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void closeDriver() {
        if (driver == null) {
            return;
        }
        try {
            driver.close();
        } catch (Exception ex) {
            log.debug("mlm_graph_gateway_close_failed message={}", ex.getMessage(), ex);
        } finally {
            driver = null;
        }
    }
}
