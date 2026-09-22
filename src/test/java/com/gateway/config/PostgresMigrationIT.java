package com.gateway.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Opt-in via -Ppostgres-it; missing PostgreSQL fails rather than silently skipping the release gate. */
class PostgresMigrationIT {
    private String url, user, password, schema;
    @BeforeEach void prepare() throws Exception {
        url = System.getenv("TEST_DATABASE_URL"); user = System.getenv("TEST_DATABASE_USER");
        password = System.getenv("TEST_DATABASE_PASSWORD");
        assertThat(url).as("TEST_DATABASE_URL is required for -Ppostgres-it").isNotBlank();
        schema = "p6_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect()) { connection.createStatement().execute("CREATE SCHEMA " + schema); }
    }
    private Connection connect() throws SQLException { return DriverManager.getConnection(url, user, password); }
    private Flyway migration(String version) {
        var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration");
        if (version != null) config.target(MigrationVersion.fromVersion(version));
        return config.load();
    }
    @AfterEach void cleanup() throws Exception {
        if (schema != null && schema.matches("p6_[a-f0-9]{32}")) {
            try (Connection connection = connect()) { connection.createStatement().execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
    private int scalar(String sql) throws Exception {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (ResultSet result = statement.executeQuery(sql)) { result.next(); return result.getInt(1); }
        }
    }
    @Test void freshInstallRunsEveryMigrationAndDisablesPublicKey() throws Exception {
        assertThat(migration(null).migrate().migrationsExecuted).isEqualTo(6);
        migration(null).validate();
        assertThat(scalar("SELECT count(*) FROM api_keys WHERE active")).isZero();
        assertThat(scalar("SELECT count(*) FROM providers")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM models")).isEqualTo(1);
        assertThat(migration(null).migrate().migrationsExecuted).isZero();
    }
    @Test void populatedMvpUpgradesWithoutLosingExistingData() throws Exception {
        migration("5").migrate();
        assertThat(scalar("SELECT count(*) FROM api_keys WHERE active")).isEqualTo(1);
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("INSERT INTO request_logs(id,request_id,status,latency_ms) VALUES ('" + UUID.randomUUID() + "','mvp-request','SUCCESS',17)");
            statement.execute("INSERT INTO api_keys(id,key_hash,label,active) VALUES ('" + UUID.randomUUID() + "','existing-customer-hash','existing',true)");
        }
        assertThat(migration(null).migrate().migrationsExecuted).isEqualTo(1);
        migration(null).validate();
        assertThat(scalar("SELECT count(*) FROM request_logs WHERE request_id='mvp-request'")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM api_keys WHERE active AND label='existing'")).isEqualTo(1);
        assertThat(scalar("SELECT count(*) FROM api_keys WHERE active AND label='local-test-key'")).isZero();
    }

    private org.springframework.context.ConfigurableApplicationContext startApplication() {
        return new org.springframework.boot.builder.SpringApplicationBuilder(com.gateway.GatewayApplication.class)
                .run("--spring.profiles.active=production",
                        "--spring.datasource.url=" + url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                        "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                        "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                        "--server.port=0", "--management.server.port=0",
                        "--management.endpoints.web.exposure.include=health,prometheus",
                        "--management.endpoint.health.show-details=never", "--spring.main.banner-mode=off");
    }

    @Test void productionStartsWithRealMigrationsAndMinimalHealth() throws Exception {
        try (var context = startApplication(); var http = java.net.http.HttpClient.newHttpClient()) {
            var environment = context.getEnvironment();
            int port = environment.getProperty("local.server.port", Integer.class);
            int management = environment.getProperty("local.management.port", Integer.class);
            var api = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/v1/providers")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(api.statusCode()).isEqualTo(401);
            assertThat(api.headers().firstValue("X-Request-Id")).isPresent();
            var health = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + management + "/actuator/health")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).doesNotContain("components", "jdbc", "password");
            var metrics = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + management + "/actuator/prometheus")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(metrics.statusCode()).isEqualTo(200);
            assertThat(metrics.body()).contains("gateway_requests");
            var publicMetrics = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/actuator/prometheus")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(publicMetrics.statusCode()).isEqualTo(401);
            assertThat(scalar("SELECT count(*) FROM api_keys WHERE active")).isZero();
        }
    }

    @Test void productionRefusesRehashedDemoCredentialAfterMigration() throws Exception {
        migration(null).migrate();
        try (Connection connection = connect(); var statement = connection.prepareStatement(
                "INSERT INTO " + schema + ".api_keys(id,key_hash,label,active) VALUES (?,?,?,true)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(DatabaseBootstrap.DEMO_KEY));
            statement.setString(3, "accidentally-reintroduced-demo"); statement.executeUpdate();
        }
        assertThatThrownBy(() -> { try (var ignored = startApplication()) { } })
                .hasRootCauseInstanceOf(IllegalStateException.class).hasStackTraceContaining("Active demo credential");
    }
}
