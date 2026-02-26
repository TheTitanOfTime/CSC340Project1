package Source;

import java.io.*;
import java.net.*;

/**
 * ServerTest.java
 *
 * Standalone test harness for the Server — no other project components needed.
 *
 * Contains two nested runnable test utilities:
 *   FakeNode   — sends UDP heartbeats and listens for TCP task forwards
 *   FakeClient — connects to the server, requests a service, sends a payload
 *
 * HOW TO RUN:
 *   1. Start Server.java
 *   2. In a second terminal: java Source.ServerTest node
 *   3. In a third terminal:  java Source.ServerTest client
 *
 * The fake node will echo whatever payload it receives back to the server.
 * The fake client will print the echoed result if everything is working.
 */
public class ServerTest {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java Source.ServerTest [node|client]");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "node"   -> FakeNode.run();
            case "client" -> FakeClient.run();
            default       -> System.out.println("Unknown argument. Use 'node' or 'client'.");
        }
    }

    // ====================================================================
    // FakeNode
    //
    // Simulates a Service Node by:
    //   1. Sending a UDP heartbeat to the server every 10 seconds so the
    //      server registers it as alive.
    //   2. Listening on TCP port 5201 for forwarded payloads from the
    //      server's Pipe, printing what it receives, and echoing it back.
    // ====================================================================
    static class FakeNode {

        static final String SERVER_IP   = "127.0.0.1";
        static final int    UDP_PORT    = 6001;   // HeartbeatMonitor port
        static final int    TCP_PORT    = 5201;   // this node's TCP listen port
        static final int    NODE_ID     = 1;
        static final String SERVICE     = Service.CSV_STATS.name(); // change as needed

        static void run() throws Exception {
            System.out.println("[FakeNode] Starting...");

            // --- Heartbeat sender thread ---
            Thread heartbeat = new Thread(() -> {
                try (DatagramSocket udpSocket = new DatagramSocket()) {
                    // Packet format expected by HeartbeatMonitor:
                    // "<nodeId>,<tcpPort>,<serviceName>"
                    String msg = NODE_ID + "," + TCP_PORT + "," + SERVICE;
                    byte[] data = msg.getBytes();
                    InetAddress serverAddr = InetAddress.getByName(SERVER_IP);

                    while (!Thread.currentThread().isInterrupted()) {
                        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, UDP_PORT);
                        udpSocket.send(packet);
                        System.out.println("[FakeNode] Sent heartbeat: " + msg);
                        Thread.sleep(10_000); // pulse every 10 seconds
                    }
                } catch (Exception e) {
                    System.err.println("[FakeNode] Heartbeat error: " + e.getMessage());
                }
            }, "FakeNode-Heartbeat");
            heartbeat.setDaemon(true);
            heartbeat.start();

            // --- TCP task listener (main thread) ---
            try (ServerSocket tcpServer = new ServerSocket(TCP_PORT)) {
                System.out.println("[FakeNode] Listening for TCP task forwards on port " + TCP_PORT);

                while (true) {
                    Socket conn = tcpServer.accept();
                    System.out.println("[FakeNode] Received connection from server Pipe.");

                    // Handle each forwarded task in its own thread
                    new Thread(() -> {
                        try (Socket s = conn) {
                            byte[] payload = s.getInputStream().readAllBytes();
                            System.out.println("[FakeNode] Received payload (" + payload.length + " bytes): "
                                    + new String(payload));

                            // Echo the payload back as the "result"
                            String response = "ECHO:" + new String(payload);
                            s.getOutputStream().write(response.getBytes());
                            s.getOutputStream().flush();
                            System.out.println("[FakeNode] Sent echo response.");
                        } catch (IOException e) {
                            System.err.println("[FakeNode] Task handler error: " + e.getMessage());
                        }
                    }, "FakeNode-TaskHandler").start();
                }
            }
        }
    }

    // ====================================================================
    // FakeClient
    //
    // Simulates a Client by:
    //   1. Connecting to the server on TCP port 5101.
    //   2. Sending a request header: "<clientId>,<serviceName>"
    //   3. Reading the available services list the server sends back.
    //   4. Sending a raw payload.
    //   5. Printing the result returned by the server.
    // ====================================================================
    static class FakeClient {

        static final String SERVER_IP = "127.0.0.1";
        static final int    SERVER_PORT = 5101;  // DoormanListener port
        static final int    CLIENT_ID   = 99;    // arbitrary test id
        static final String SERVICE     = Service.CSV_STATS.name(); // must match FakeNode
        static final String PAYLOAD     = "col1,col2\n1,2\n3,4\n5,6"; // sample CSV

        static void run() throws Exception {
            System.out.println("[FakeClient] Connecting to server at "
                    + SERVER_IP + ":" + SERVER_PORT);

            try (Socket socket = new Socket(SERVER_IP, SERVER_PORT)) {
                OutputStream out = socket.getOutputStream();
                InputStream  in  = socket.getInputStream();

                // Step 1 — send request header
                String header = CLIENT_ID + "," + SERVICE + "\n";
                out.write(header.getBytes());
                out.flush();
                System.out.println("[FakeClient] Sent header: " + header.trim());

                // Step 2 — read available services from server
                String serviceList = readLine(in);
                System.out.println("[FakeClient] Server says: " + serviceList);

                // Step 3 — send payload then close write side
                out.write(PAYLOAD.getBytes());
                out.flush();
                socket.shutdownOutput(); // signal EOF so server's Pipe stops blocking
                System.out.println("[FakeClient] Sent payload: " + PAYLOAD);

                // Step 4 — read and print the result
                byte[] result = in.readAllBytes();
                System.out.println("[FakeClient] Result from server: " + new String(result));
            }
        }

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
}
