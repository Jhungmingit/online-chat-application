import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChatServer
 *
 * A multi-client chat server built on TCP sockets.
 *
 * How it works:
 *   1. The server listens on a port using a ServerSocket.
 *   2. Every time a client connects, the server gives that client a unique
 *      user ID (User 1, User 2, ...) and starts a new thread to serve it.
 *   3. Whenever a client sends a line of text, the server broadcasts it to
 *      every connected client.
 *   4. The server keeps a list of connected users, which clients can view
 *      by typing /users.
 *
 * Usage:  java ChatServer [port]      (default port is 5000)
 */
public class ChatServer {

    /** Port used when none is given on the command line. */
    private static final int DEFAULT_PORT = 5000;

    /** All connected clients, keyed by their unique user ID. */
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();

    /** Hands out the next unique user ID; thread-safe. */
    private final AtomicInteger nextUserId = new AtomicInteger(1);

    // ------------------------------------------------------------------
    // Program entry point
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        // Optionally read the port number from the command line.
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port '" + args[0] + "'. Using " + DEFAULT_PORT + ".");
            }
        }

        new ChatServer().start(port);
    }

    // ------------------------------------------------------------------
    // Server logic
    // ------------------------------------------------------------------

    /**
     * Opens the server socket and accepts clients forever.
     * Each accepted client is handled on its own thread so that one slow
     * client can never block the others.
     */
    private void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server started on port " + port);
            System.out.println("Waiting for clients... (press Ctrl+C to stop)");

            while (true) {
                // accept() blocks until a client connects.
                Socket socket = serverSocket.accept();

                int userId = nextUserId.getAndIncrement();
                ClientHandler handler = new ClientHandler(socket, userId);
                clients.put(userId, handler);

                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Sends a message to every connected client (including the sender,
     * so everyone sees the conversation in the same order).
     */
    private void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
    }

    /** Builds a sorted, comma-separated list of connected users. */
    private String userList() {
        // TreeMap keeps the IDs in ascending order.
        Map<Integer, ClientHandler> sorted = new TreeMap<>(clients);
        StringBuilder sb = new StringBuilder();
        for (Integer id : sorted.keySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("User ").append(id);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // One ClientHandler per connected client
    // ------------------------------------------------------------------

    /**
     * Serves a single client: reads its messages and forwards them to
     * everyone. Runs on its own thread.
     */
    private class ClientHandler implements Runnable {

        private final Socket socket;
        private final int userId;
        private PrintWriter out;

        ClientHandler(Socket socket, int userId) {
            this.socket = socket;
            this.userId = userId;
        }

        /** Sends one line of text to this client. */
        void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                // 'true' turns on auto-flush so each println is sent immediately.
                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                System.out.println("User " + userId + " connected from "
                        + socket.getInetAddress().getHostAddress()
                        + " (" + clients.size() + " online)");

                // Greet the new client, then tell everyone else.
                send("Welcome! You are User " + userId + ".");
                send("Online now: " + userList());
                send("Commands: /users = list users, /quit = leave");
                broadcast("*** User " + userId + " joined the chat ***");

                // Main loop: read lines until the client disconnects.
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;                       // ignore blank lines
                    } else if (line.equalsIgnoreCase("/quit")) {
                        break;                          // client wants to leave
                    } else if (line.equalsIgnoreCase("/users")) {
                        send("Online now: " + userList());
                    } else {
                        broadcast("[User " + userId + "] " + line);
                    }
                }
            } catch (IOException e) {
                // Connection dropped unexpectedly; handled in 'finally'.
                System.out.println("Connection problem with User " + userId + ": " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        /** Removes this client, closes its socket and tells the others. */
        private void disconnect() {
            clients.remove(userId);
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing more we can do if closing fails.
            }
            System.out.println("User " + userId + " disconnected (" + clients.size() + " online)");
            broadcast("*** User " + userId + " left the chat ***");
        }
    }
}
