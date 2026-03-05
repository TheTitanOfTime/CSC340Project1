package Services.Base64;

import Source.Node;
import Source.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base64Node — Microservice node for BASE64_ENCODE_DECODE (service #2).
 *
 * Uses Pattern A: overrides handleRequest() and processes everything
 * in-process using the standard Java Base64 library. No subprocess needed.
 *
 * Expected inbound JSON (forwarded by Pipe):
 *   {
 *     "service"   : 2,
 *     "operation" : "encode" | "decode",
 *     "filename"  : "example.txt",
 *     "data"      : "<string to encode>  OR  <base64 string to decode>"
 *   }
 *
 * Response JSON written back to Pipe → client:
 *   { "status": "ok",    "result": "<processed string>" }
 *   { "status": "error", "message": "<details>" }
 *
 * Directory: Services/Base64/Base64Node.java
 */
public class Base64Node extends Node {

    private static final int NODE_ID = 2;

    private static final Pattern OPERATION_PATTERN =
            Pattern.compile("\"operation\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DATA_PATTERN =
            Pattern.compile("\"data\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    // -----------------------------------------------------------------------
    // Node identity
    // -----------------------------------------------------------------------

    @Override
    protected int getNodeId() { return NODE_ID; }

    @Override
    protected Service getService() { return Service.BASE64_ENCODE_DECODE; }

    // -----------------------------------------------------------------------
    // Pattern A — in-process request handler
    // -----------------------------------------------------------------------

    /**
     * Parses the JSON payload, performs encode or decode, returns JSON result.
     * Runs entirely in Java — no subprocess, no classpath issues.
     */
    @Override
    protected byte[] handleRequest(byte[] payload) {
        String json = new String(payload, StandardCharsets.UTF_8);

        String operation = extract(OPERATION_PATTERN, json);
        String data      = extract(DATA_PATTERN,      json);

        if (operation == null || operation.isBlank()) {
            return error("Missing \"operation\" field. Expected \"encode\" or \"decode\".");
        }
        if (data == null) {
            return error("Missing \"data\" field.");
        }

        // Unescape JSON-escaped characters in the extracted data value
        // (e.g. \" → ", \\ → \) so we operate on the real string.
        data = data.replace("\\\"", "\"").replace("\\\\", "\\");

        return switch (operation.trim().toLowerCase()) {
            case "encode" -> {
                String encoded = ENCODER.encodeToString(data.getBytes(StandardCharsets.UTF_8));
                yield success(encoded);
            }
            case "decode" -> {
                try {
                    String decoded = new String(DECODER.decode(data), StandardCharsets.UTF_8);
                    yield success(decoded);
                } catch (IllegalArgumentException e) {
                    yield error("Invalid Base64 input: " + e.getMessage());
                }
            }
            default -> error("Unknown operation \"" + operation
                    + "\". Expected \"encode\" or \"decode\".");
        };
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String extract(Pattern p, String json) {
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static byte[] success(String result) {
        String escaped = result.replace("\\", "\\\\").replace("\"", "\\\"");
        return ("{\"status\":\"ok\",\"result\":\"" + escaped + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] error(String message) {
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        return ("{\"status\":\"error\",\"message\":\"" + escaped + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("[Base64Node] Starting...");
        new Base64Node().run();
    }
}