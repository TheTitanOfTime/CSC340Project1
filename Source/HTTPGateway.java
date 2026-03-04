package Source;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import Services.Compression.CompressionService;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String DOORMAN_HOST  = "127.0.0.1";
    private static final int    DOORMAN_PORT  = DoormanListener.TCP_PORT;

    /**
     * Root directory for static frontend files.
     * Default works when the server is launched from the project root.
     * Override on AWS: -Dfrontend.dir=/home/ec2-user/Frontend
     */
    private static final String FRONTEND_DIR =
            System.getProperty("frontend.dir", "Frontend");

    // Compression handled in-process (no separate CompressionNode required)
    private static final CompressionService COMPRESSION_SVC = new CompressionService();
    private static final Base64.Decoder     B64_DEC = Base64.getDecoder();
    private static final Base64.Encoder     B64_ENC = Base64.getEncoder();
    private static final Pattern OP_PAT = Pattern.compile("\"operation\"\\s*:\\s*\"([^\"]+)\"");

    // -----------------------------------------------------------------------
    // Runnable entry point
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(HTTP_PORT), /* backlog */ 32);
            server.createContext("/ping",         this::handlePing);
            server.createContext("/api/nbody",    this::handleNBody);
            server.createContext("/api/compress", this::handleCompress);
            server.createContext("/",             this::handleStatic);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.printf("[HTTPGateway] HTTP server listening on port %d%n", HTTP_PORT);
            System.out.printf("[HTTPGateway] Serving frontend from: %s%n",
                    new File(FRONTEND_DIR).getCanonicalPath());
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
    // POST /api/compress
    // -----------------------------------------------------------------------

    private void handleCompress(HttpExchange ex) throws IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        byte[] payload = ex.getRequestBody().readAllBytes();
        if (payload.length == 0) {
            sendError(ex, "Empty request body.");
            return;
        }

        String json      = new String(payload, StandardCharsets.UTF_8);
        String operation = extractField(OP_PAT, json);
        String filename  = extractJsonString(json, "filename");
        String data      = extractJsonString(json, "data");

        if (operation == null || operation.isBlank()) {
            sendCompressError(ex, "Missing \"operation\" field.");
            return;
        }
        if (data == null) {
            sendCompressError(ex, "Missing \"data\" field.");
            return;
        }
        if (filename == null || filename.isBlank()) filename = "file";

        byte[] inputBytes;
        try {
            inputBytes = B64_DEC.decode(data);
        } catch (IllegalArgumentException e) {
            sendCompressError(ex, "Invalid Base64 data: " + e.getMessage());
            return;
        }

        try {
            byte[] outputBytes;
            String outName;
            String op = operation.trim().toLowerCase();
            if (op.equals("compress")) {
                outputBytes = COMPRESSION_SVC.compress(inputBytes);
                outName     = filename + ".z";
            } else if (op.equals("decompress")) {
                outputBytes = COMPRESSION_SVC.decompress(inputBytes);
                outName     = filename.endsWith(".z")
                        ? filename.substring(0, filename.length() - 2)
                        : filename + ".decompressed";
            } else {
                sendCompressError(ex, "Unknown operation: " + operation);
                return;
            }

            String encResult  = B64_ENC.encodeToString(outputBytes);
            String escName    = outName.replace("\\", "\\\\").replace("\"", "\\\"");
            String response   = "{\"status\":\"ok\",\"result\":\"" + encResult + "\",\"filename\":\"" + escName + "\"}";
            byte[] body       = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }

        } catch (Exception e) {
            sendCompressError(ex, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void sendCompressError(HttpExchange ex, String message) throws IOException {
        String esc  = message.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] body = ("{\"status\":\"error\",\"message\":\"" + esc + "\"}").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static String extractField(Pattern p, String json) {
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extracts the value of a JSON string field without regex, avoiding
     * StackOverflowError that occurs when regex group quantifiers recurse
     * over large values (e.g. base64-encoded file data).
     */
    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int pos = json.indexOf(needle);
        if (pos < 0) return null;
        pos += needle.length();
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\' && pos < json.length()) c = json.charAt(pos++);
            sb.append(c);
        }
        return null; // unterminated string
    }

    // -----------------------------------------------------------------------
    // GET /* — static frontend files
    // -----------------------------------------------------------------------

    /**
     * Serves static files from FRONTEND_DIR.
     *   /                        → Frontend/src/index.html
     *   /src/Gravitational.html  → Frontend/src/Gravitational.html
     *   /Resources/images/x.png  → Frontend/Resources/images/x.png
     *
     * Path traversal is blocked by canonical-path check.
     */
    private void handleStatic(HttpExchange ex) throws IOException {
        String uriPath = ex.getRequestURI().getPath();

        if (uriPath.equals("/")) uriPath = "/src/index.html";

        File root = new File(FRONTEND_DIR).getCanonicalFile();
        File file = new File(root, uriPath).getCanonicalFile();

        // Security: block any path that escapes the frontend root
        if (!file.getPath().startsWith(root.getPath())) {
            send404(ex); return;
        }

        if (!file.exists() || !file.isFile()) {
            send404(ex); return;
        }

        byte[] body = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().set("Content-Type", mimeType(file.getName()));
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static String mimeType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css"))  return "text/css; charset=utf-8";
        if (name.endsWith(".js"))   return "application/javascript";
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".ico"))  return "image/x-icon";
        if (name.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private static void send404(HttpExchange ex) throws IOException {
        byte[] body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain");
        ex.sendResponseHeaders(404, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
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
