package org.operaton.examples.runtimequarkus;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    @SuppressWarnings("rawtypes")
    private static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer("postgres:16-alpine");

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        return Map.of(
            "quarkus.datasource.operaton-db.jdbc.url", POSTGRES.getJdbcUrl(),
            "quarkus.datasource.operaton-db.username", POSTGRES.getUsername(),
            "quarkus.datasource.operaton-db.password", POSTGRES.getPassword()
        );
    }

    @Override
    public void stop() {
        POSTGRES.stop();
    }
}
