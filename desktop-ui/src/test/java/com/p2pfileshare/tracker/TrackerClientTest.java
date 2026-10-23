package com.p2pfileshare.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TrackerClientTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int responseStatus;
    private volatile String responseBody;

    @BeforeEach void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        responseStatus = 200;
        responseBody = "{\"token\":\"fake-jwt\",\"user\":{\"id\":1}}";
    }

    @AfterEach void stopServer() { server.stop(0); }

    private void handle(HttpExchange exchange) throws IOException {
        method.set(exchange.getRequestMethod()); path.set(exchange.getRequestURI().toString());
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test void loginUsesApiFieldsAndBearerTokenForFileListing() throws Exception {
        TrackerClient client = new TrackerClient(baseUrl);
        client.login("user@example.com", "temporary-pass".toCharArray());
        assertEquals("POST", method.get());
        assertEquals("/api/auth/login", path.get());
        assertEquals("user@example.com", new ObjectMapper().readTree(body.get()).path("email").asText());

        responseBody = "{\"content\":[],\"page\":0}";
        client.listFiles("a b", 2, 10);
        assertEquals("/api/files?search=a+b&page=2&size=10", path.get());
        assertEquals("Bearer fake-jwt", authorization.get());
    }

    @Test void registerUsesTrackerRegisterPath() throws Exception {
        TrackerClient client = new TrackerClient(baseUrl);
        client.register("user@example.com", "temporary-pass".toCharArray());
        assertEquals("POST", method.get());
        assertEquals("/api/auth/register", path.get());
        assertEquals("temporary-pass", new ObjectMapper().readTree(body.get()).path("password").asText());
        assertNull(authorization.get());
    }

    @Test void unauthorizedResponseClearsSessionAndReports401() throws Exception {
        TrackerClient client = new TrackerClient(baseUrl);
        client.login("user@example.com", "temporary-pass".toCharArray());
        responseStatus = 401;
        assertThrows(TrackerClient.UnauthorizedException.class, client::onlinePeers);
        assertNull(clientToken(client));
    }

    private static String clientToken(TrackerClient client) throws Exception {
        var field = TrackerClient.class.getDeclaredField("token");
        field.setAccessible(true);
        return (String) field.get(client);
    }
}
