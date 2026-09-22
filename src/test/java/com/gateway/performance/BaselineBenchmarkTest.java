package com.gateway.performance;

import com.gateway.auth.*;
import com.gateway.config.*;
import com.gateway.provider.ProviderRegistry;
import com.gateway.support.MockUpstream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Reproducible small non-streaming baseline, deliberately separate from the production load gate. */
@EnabledIfSystemProperty(named = "gateway.benchmark", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:benchmark;DB_CLOSE_DELAY=-1", "management.server.port=0"})
class BaselineBenchmarkTest {
    @Autowired Environment env;
    @Autowired ApiKeyRepository keys;
    @Autowired ProviderConfigRepository providers;
    @Autowired ModelConfigRepository models;
    @Autowired ProviderRegistry registry;
    @Autowired BCryptPasswordEncoder encoder;

    @Test void measureLoopbackGatewayWithAuthenticationAndDiagnosticWrites() throws Exception {
        try (var upstream = new MockUpstream(); var http = HttpClient.newHttpClient()) {
            var provider = providers.save(ProviderConfig.builder().id(UUID.randomUUID()).name("benchmark")
                    .type("ollama").baseUrl(upstream.url()).active(true).defaultProvider(true).build());
            models.save(ModelConfig.builder().id(UUID.randomUUID()).provider(provider).name("fixture").active(true).build());
            keys.save(ApiKey.builder().id(UUID.randomUUID()).keyHash(encoder.encode("benchmark-local-key"))
                    .label("benchmark").active(true).createdAt(LocalDateTime.now()).build());
            registry.init();
            String prompt = "a".repeat(1024);
            byte[] body = JsonMapper.builder().build().writeValueAsBytes(Map.of("model", "fixture", "prompt", prompt));
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + env.getProperty("local.server.port") + "/v1/inference"))
                    .header("Content-Type", "application/json").header("X-API-Key", "benchmark-local-key")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var direct = HttpRequest.newBuilder(URI.create(upstream.url() + "/api/generate"))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            for (int i = 0; i < 20; i++) execute(http, request);
            var output = new LinkedHashMap<String, Object>();
            output.put("scope", "loopback HTTP, real BCrypt, H2 diagnostic writes, synthetic Ollama; not production capacity");
            output.put("java", System.getProperty("java.version")); output.put("os", System.getProperty("os.name"));
            output.put("cpus", Runtime.getRuntime().availableProcessors()); output.put("inputCharacters", 1024);
            output.put("warmupRequests", 20); output.put("directSequential", measure(http, direct, 100, 1));
            output.put("gatewaySequential", measure(http, request, 100, 1));
            output.put("gatewayConcurrent", measure(http, request, 100, 10));
            output.put("heapUsedBytesAtEnd", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            output.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target/phase6-baseline.json"), JsonMapper.builder().build()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(output));
        }
    }
    private Map<String, Object> measure(HttpClient http, HttpRequest request, int count, int concurrency) throws Exception {
        long start = System.nanoTime(); List<Double> timings = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(concurrency)) {
            List<Future<Double>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> execute(http, request)));
            for (var future : futures) timings.add(future.get(30, TimeUnit.SECONDS));
        }
        Collections.sort(timings);
        return Map.of("requests", count, "concurrency", concurrency, "p50Ms", timings.get(49),
                "p95Ms", timings.get(94), "p99Ms", timings.get(98),
                "requestsPerSecond", count * 1e9 / (System.nanoTime() - start));
    }
    private double execute(HttpClient http, HttpRequest request) throws Exception {
        long start = System.nanoTime();
        var result = http.send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(result.statusCode()).isEqualTo(200);
        return (System.nanoTime() - start) / 1e6;
    }
}
