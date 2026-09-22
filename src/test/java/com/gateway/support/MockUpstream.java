package com.gateway.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Real loopback HTTP fixture; supports delays, status failures and malformed/oversized bodies. */
public final class MockUpstream implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private volatile HttpHandler responder = exchange -> respond(exchange, 200, "{\"response\":\"ok\",\"done\":true}");
    public final AtomicInteger calls = new AtomicInteger();
    public MockUpstream() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            try { responder.handle(exchange); } catch (IOException ignored) {
                // Client cancellation is expected in deadline tests.
            } finally { exchange.close(); }
        });
        server.setExecutor(executor); server.start();
    }
    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public void respondsWith(HttpHandler responder) { this.responder = responder; }
    public static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
