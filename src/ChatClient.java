import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * ChatClient
 *
 * A text-based chat client that connects to a ChatServer.
 *
 * How it works:a
 *   - The MAIN thread reads what you type and sends it to the server.
 *   - A second RECEIVER thread listens for messages from the server and
 *     prints them as they arrive. Two threads are needed because waiting
 *     for keyboard input and waiting for network input both block.
 *
 * Usage:  java ChatClient [host] [port]
 *         (defaults: localhost 5000)
 */
public class ChatClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    /** Set to true when the user chooses to leave, so we don't print a "disconnected" error. */
    private static volatile boolean quitting = false;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        // Optional command-line arguments: host and port.
        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port '" + args[1] + "'. Using " + DEFAULT_PORT + ".");
            }
        }

        printBanner(host, port);

        // try-with-resources closes the socket automatically when we finish.
        try (Socket socket = new Socket(host, port)) {

            BufferedReader fromServer = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter toServer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

            // Start the receiver thread. It is a daemon thread, so it will not
            // keep the program alive once the main thread is finished.
            Thread receiver = new Thread(() -> receiveMessages(fromServer));
            receiver.setDaemon(true);
            receiver.start();

            // Main loop: send each line the user types.
            String line;
            while ((line = keyboard.readLine()) != null) {
                toServer.println(line);
                if (line.trim().equalsIgnoreCase("/quit")) {
                    quitting = true;
                    break;                       // user asked to leave
                }
            }

            System.out.println("You have left the chat. Goodbye!");

        } catch (IOException e) {
            System.out.println("Could not connect to " + host + ":" + port
                    + " (" + e.getMessage() + ")");
            System.out.println("Is the server running?");
        }
    }

    /**
     * Runs on the receiver thread: prints every line the server sends.
     * When the server closes the connection, readLine() returns null.
     */
    private static void receiveMessages(BufferedReader fromServer) {
        try {
            String message;
            while ((message = fromServer.readLine()) != null) {
                System.out.println(message);
            }
        } catch (IOException e) {
            // The socket was closed (we quit, or the server stopped).
        }
        if (!quitting) {
            System.out.println("Disconnected from the server.");
            System.exit(0);
        }
    }

    /** Prints the title box shown when the client starts. */
    private static void printBanner(String host, int port) {
        System.out.println("==========================================");
        System.out.println("            JAVA CHAT CLIENT");
        System.out.println("==========================================");
        System.out.println("Connecting to " + host + ":" + port + " ...");
        System.out.println("Type a message and press Enter to send.");
        System.out.println("------------------------------------------");
    }
}
