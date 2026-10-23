package com.p2pfileshare.tracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.util.HashUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Small typed facade for the tracker's REST API. Authentication material stays in memory. */
public final class TrackerClient {
    private final URI baseUri;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private volatile String token;

    public TrackerClient() {
        this(System.getenv().getOrDefault("TRACKER_URL", "http://localhost:8080"), HttpClient.newHttpClient(), new ObjectMapper());
    }

    public TrackerClient(String baseUrl) {
        this(baseUrl, HttpClient.newHttpClient(), new ObjectMapper());
    }

    TrackerClient(String baseUrl, HttpClient http, ObjectMapper mapper) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUri = URI.create(normalized);
        this.http = http;
        this.mapper = mapper;
    }

    public JsonNode register(String email, char[] password) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode().put("email", email).put("password", new String(password));
        return send("POST", "/api/auth/register", body, false);
    }

    public JsonNode login(String email, char[] password) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode().put("email", email).put("password", new String(password));
        JsonNode response = send("POST", "/api/auth/login", body, false);
        token = response.path("token").asText(null);
        return response;
    }

    public JsonNode listFiles(String search, int page, int size) throws IOException, InterruptedException {
        return send("GET", "/api/files?search=" + encode(search == null ? "" : search) + "&page=" + page + "&size=" + size, null, true);
    }

    public JsonNode onlinePeers() throws IOException, InterruptedException { return send("GET", "/api/peers/online", null, true); }
    public JsonNode transfers() throws IOException, InterruptedException { return send("GET", "/api/transfers?page=0&size=50", null, true); }
    public JsonNode sources(long fileId) throws IOException, InterruptedException { return send("GET", "/api/files/" + fileId + "/sources", null, true); }

    public JsonNode heartbeat(String peerUid, int port, String ipAddress) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode().put("peerUid", peerUid).put("port", port);
        if (ipAddress != null && !ipAddress.isBlank()) body.put("ipAddress", ipAddress);
        return send("POST", "/api/peers/heartbeat", body, true);
    }

    public JsonNode registerFile(FileManifest manifest) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode()
                .put("name", manifest.getFileName()).put("sizeBytes", manifest.getFileSize())
                .put("chunkSize", manifest.getChunkSize()).put("fileHash", fileHash(manifest))
                .put("visibility", "PUBLIC");
        ArrayNode chunks = body.putArray("chunks");
        manifest.getChunks().forEach(chunk -> chunks.addObject().put("index", chunk.getIndex()).put("sha256", chunk.getSha256()).put("sizeBytes", chunk.getSize()));
        return send("POST", "/api/files", body, true);
    }

    public JsonNode createTransfer(long fileId, String sourcePeerUid) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode().put("fileId", fileId);
        if (sourcePeerUid != null) body.put("sourcePeerUid", sourcePeerUid);
        return send("POST", "/api/transfers", body, true);
    }

    public void logout() { token = null; }

    private JsonNode send(String method, String path, JsonNode body, boolean authenticated) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path)).header("Accept", "application/json");
        if (body != null) builder.header("Content-Type", "application/json");
        if (authenticated && token != null) builder.header("Authorization", "Bearer " + token);
        if ("GET".equals(method)) builder.GET();
        else builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) { logout(); throw new UnauthorizedException(); }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = response.body();
            try { message = mapper.readTree(response.body()).path("message").asText(message); } catch (Exception ignored) { }
            throw new TrackerException(response.statusCode(), message);
        }
        return response.body().isBlank() ? mapper.nullNode() : mapper.readTree(response.body());
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static String fileHash(FileManifest manifest) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(manifest.getFilePath())) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static class TrackerException extends IOException {
        private final int status;
        public TrackerException(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
    public static final class UnauthorizedException extends TrackerException {
        public UnauthorizedException() { super(401, "Session expired. Please sign in again."); }
    }
}
