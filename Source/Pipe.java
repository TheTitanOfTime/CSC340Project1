package Source;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Pipe (Client Thread)
 *
 * Spawned by DoormanListener for every incoming client connection.
 * Lifecycle:
 *   1. Read the service request header from the client.
 *      Expected format (plain text, UTF-8, newline-terminated):
 *        "<clientId>,<serviceName>\n"
 *
 *   2. Send the client the current list of available services so they
 *      can confirm the service is live before sending data.
 *      Format: comma-separated service names, newline-terminated.
 *
 *   3. Wait for the client to send its raw payload (all remaining bytes
 *      until the client closes its output / sends EOF).
 *
 *   4. Look up a live Service Node for the requested service.
 *      If none found, reply with an error message and close.
 *
 *   5. Open a TCP connection to the Service Node, forward the payload,
 *      close the write side, then stream the result back to the client.
 *
 *   6. Close both sockets.
 */
public class Pipe implements Runnable {

    private final Socket        clientSocket;
    private final int           clientId;
    private final NodeRegistry  registry;

    public Pipe(Socket clientSocket, int clientId, NodeRegistry registry) {
        this.clientSocket = clientSocket;
        this.clientId     = clientId;
        this.registry     = registry;
    }

    @Override
    public void run() {
        System.out.printf("[Pipe-%d] Handling client %s%n",
                clientId, clientSocket.getRemoteSocketAddress());

        try (Socket cs = clientSocket) {
            InputStream  clientIn  = cs.getInputStream();
            OutputStream clientOut = cs.getOutputStream();

            // ---------------------------------------------------------- //
            // Step 1 — read the request header line                       //
            // ---------------------------------------------------------- //
            String header = readLine(clientIn);
            if (header == null || header.isEmpty()) {
                sendError(clientOut, "Empty request.");
                return;
            }

            String[] parts = header.split(",", 2);
            if (parts.length < 2) {
                sendError(clientOut, "Malformed header. Expected: <clientId>,<serviceName>");
                return;
            }

            // The client ID in the header should match what the server assigned.
            // We log a warning if they differ but continue anyway.
            int     claimedId = parseIntSafe(parts[0].trim());
            Service requested = Service.fromString(parts[1].trim());

            if (claimedId != clientId) {
                System.err.printf("[Pipe-%d] WARNING: client claims id=%d%n", clientId, claimedId);
            }
            if (requested == null) {
                sendError(clientOut, "Unknown service: " + parts[1].trim());
                return;
            }

            // ---------------------------------------------------------- //
            // Step 2 — send available services back to the client         //
            // ---------------------------------------------------------- //
            String availableServices = buildServiceList();
            clientOut.write((availableServices + "\n").getBytes());
            clientOut.flush();

            // ---------------------------------------------------------- //
            // Step 3 — read the raw payload from the client               //
            // ---------------------------------------------------------- //
            byte[] payload = clientIn.readAllBytes(); // blocks until client closes write-half
            System.out.printf("[Pipe-%d] Received %d bytes payload for service %s%n",
                    clientId, payload.length, requested);

            // ---------------------------------------------------------- //
            // Step 4 — find a live node for the requested service         //
            // ---------------------------------------------------------- //
            NodeInfo targetNode = registry.findAliveNode(requested);
            if (targetNode == null) {
                sendError(clientOut, "SERVICE_UNAVAILABLE: No live node for " + requested);
                return;
            }

            // ---------------------------------------------------------- //
            // Step 5 — forward payload to SN and stream result back       //
            // ---------------------------------------------------------- //
            forwardToNode(targetNode, payload, clientOut);

        } catch (IOException e) {
            System.err.printf("[Pipe-%d] IO error: %s%n", clientId, e.getMessage());
        }

        System.out.printf("[Pipe-%d] Done.%n", clientId);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Opens a TCP connection to the Service Node, sends the payload,
     * signals EOF on the write side, then copies all response bytes back
     * to the client.
     */
    private void forwardToNode(NodeInfo node, byte[] payload, OutputStream clientOut)
            throws IOException {

        System.out.printf("[Pipe-%d] Forwarding to node %d at %s:%d%n",
                clientId, node.getNodeId(), node.getIp(), node.getTcpPort());

        try (Socket nodeSocket = new Socket(node.getIp(), node.getTcpPort())) {
            OutputStream nodeOut = nodeSocket.getOutputStream();
            InputStream  nodeIn  = nodeSocket.getInputStream();

            // Send the payload to the SN.
            nodeOut.write(payload);
            nodeOut.flush();
            nodeSocket.shutdownOutput(); // signal EOF to SN

            // Stream the result back to the client.
            byte[] result = nodeIn.readAllBytes();
            clientOut.write(result);
            clientOut.flush();

            System.out.printf("[Pipe-%d] Forwarded %d result bytes back to client.%n",
                    clientId, result.length);
        }
    }

    /** Reads bytes until '\n' or stream end. Returns null on immediate EOF. */
    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return sb.length() == 0 && b == -1 ? null : sb.toString();
    }

    private void sendError(OutputStream out, String message) throws IOException {
        String response = "ERROR: " + message + "\n";
        out.write(response.getBytes());
        out.flush();
        System.err.printf("[Pipe-%d] Sent error to client: %s%n", clientId, message);
    }

    private String buildServiceList() {
        StringBuilder sb = new StringBuilder("AVAILABLE_SERVICES:");
        for (Service s : registry.getAvailableServices()) {
            sb.append(s.name()).append(",");
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return -1; }
    }
}
