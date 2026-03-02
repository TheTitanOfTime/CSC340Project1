package Source;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * HTTPGateway — Lightweight HTTP bridge that lets browser frontends reach the
 * microservices cluster without needing raw TCP sockets.
 *
 * Runs as a daemon thread alongside DoormanListener and HeartbeatMonitor.
 * Listens on HTTP_PORT (5000) and proxies to DoormanListener on TCP 5101.
 *
 * Endpoints:
 *   GET  /ping       → { "status": "alive" }
 *   POST /api/nbody  → proxies JSON body through the full Pipe → NBodyNode
 *                       pipeline and returns the service result JSON.
 *
 * CORS headers are sent on every response so the browser can call this
 * from any local file or live-server origin.
 *
 * TCP protocol followed (matches Pipe.java exactly):
 *   1. Connect to DoormanListener on TCP 5101.
 *   2. Write the JSON payload.
 *   3. Call shutdownOutput() — signals EOF; Pipe's readAllBytes() returns.
 *   4. Read the AVAILABLE_SERVICES line (terminated with '\n') — discarded.
 *   5. Read remaining bytes — the service node result JSON.
 *   6. Return result as the HTTP response body.
 */
public class HTTPGateway implements Runnable {

    public static final int HTTP_PORT = 5050;

    private static final String DOORMAN_HOST = "127.0.0.1";
    private static final int    DOORMAN_PORT = DoormanListener.TCP_PORT;

    // -----------------------------------------------------------------------
    // Runnable entry point
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(HTTP_PORT), /* backlog */ 32);
            server.createContext("/ping",      this::handlePing);
            server.createContext("/api/nbody", this::handleNBody);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.printf("[HTTPGateway] HTTP server listening on port %d%n", HTTP_PORT);
        } catch (IOException e) {
            System.err.printf("[HTTPGateway] Failed to start: %s%n", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // GET /ping
    // -----------------------------------------------------------------------

    private void handlePing(HttpExchange ex) throws IOException {
        byte[] body = "{\"status\":\"alive\"}".getBytes(StandardCharsets.UTF_8);
        addCors(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    // -----------------------------------------------------------------------
    // POST /api/nbody
    // -----------------------------------------------------------------------

    private void handleNBody(HttpExchange ex) throws IOException {
        addCors(ex);

        // Browser sends a preflight OPTIONS before the real POST
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        byte[] payload = ex.getRequestBody().readAllBytes();
        if (payload.length == 0) {
            sendError(ex, "Empty request body.");
            return;
        }

        // ── Proxy through the full TCP pipeline ──────────────────────────
        try (Socket socket = new Socket(DOORMAN_HOST, DOORMAN_PORT)) {

            // Step 2: send JSON payload
            OutputStream out = socket.getOutputStream();
            out.write(payload);
            out.flush();

            // Step 3: signal EOF so Pipe's readAllBytes() returns
            socket.shutdownOutput();

            InputStream in = socket.getInputStream();

            // Step 4: skip the AVAILABLE_SERVICES line
            readLine(in);

            // Step 5: read the service result
            byte[] result = in.readAllBytes();

            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, result.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(result); }

        } catch (IOException e) {
            sendError(ex, "Could not reach DoormanListener: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendError(HttpExchange ex, String message) throws IOException {
        String esc  = message.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] body = ("{\"status\":\"error\",\"message\":\"" + esc + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        addCors(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(500, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    /** Reads one '\n'-terminated line from the stream (used to consume the AVAILABLE_SERVICES line). */
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return sb.toString();
    }
}
