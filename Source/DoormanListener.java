package Source;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DoormanListener
 *
 * The front door of the server. Runs on TCP port 5101.
 * Accepts incoming client connections in a loop and spawns a new
 * Pipe thread for each one, assigning each client a unique integer ID.
 *
 * The main server thread is never blocked — it always returns to listening
 * immediately after handing the connection off to a Pipe.
 */
public class DoormanListener implements Runnable {

    public static final int TCP_PORT = 5101;

    private final NodeRegistry    registry;
    private final AtomicInteger   clientIdCounter = new AtomicInteger(1);

    public DoormanListener(NodeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.printf("[DoormanListener] Accepting TCP connections on port %d%n", TCP_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                // Blocks here until a client connects.
                Socket clientSocket = serverSocket.accept();

                int clientId = clientIdCounter.getAndIncrement();
                System.out.printf("[DoormanListener] New client connected from %s — assigned id=%d%n",
                        clientSocket.getRemoteSocketAddress(), clientId);

                // Spawn a Pipe thread to service this client.
                Pipe pipe = new Pipe(clientSocket, clientId, registry);
                Thread pipeThread = new Thread(pipe, "Pipe-" + clientId);
                pipeThread.setDaemon(true);
                pipeThread.start();
            }
        } catch (Exception e) {
            System.err.println("[DoormanListener] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
