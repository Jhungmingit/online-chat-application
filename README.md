# Online Chat Application (Java Sockets)

A simple multi-user chat built with Java socket programming. A central server accepts many clients, gives each a unique user ID, and broadcasts every message to everyone connected. The client has a text-based interface.

## Files

| File | Purpose |
|---|---|
| `ChatServer.java` | The server: accepts connections, assigns IDs, tracks users, broadcasts messages |
| `ChatClient.java` | The text-based client: sends what you type, shows what others say |

## Requirements

- Java JDK 11 or newer (check with `java -version` and `javac -version`)

## How to run

Open a terminal in the folder containing the two files.

**1. Compile**

```bash
javac ChatServer.java ChatClient.java
```

**2. Start the server** (in the first terminal)

```bash
java ChatServer
```

It listens on port 5000 by default. To use another port: `java ChatServer 6000`.

**3. Start one or more clients** (each in its own new terminal)

```bash
java ChatClient
```

To connect to another host or port: `java ChatClient <host> <port>`, for example `java ChatClient 192.168.1.20 5000`.

**4. Chat.** Type a message and press Enter. Everyone connected sees it.

### Commands

| Command | What it does |
|---|---|
| `/users` | Shows who is online |
| `/quit` | Leaves the chat |

## Example session

```
==========================================
            JAVA CHAT CLIENT
==========================================
Connecting to localhost:5000 ...
Type a message and press Enter to send.
------------------------------------------
Welcome! You are User 1.
Online now: User 1
Commands: /users = list users, /quit = leave
*** User 1 joined the chat ***
*** User 2 joined the chat ***
[User 1] hello from A
[User 2] hi A, this is B
```

## How it works

### Server (`ChatServer`)

- Opens a `ServerSocket` and loops forever calling `accept()`, which waits for a client.
- Each new client gets a **unique user ID** from an `AtomicInteger` counter (User 1, User 2, ...), so two clients can never share an ID even if they connect at the same moment.
- Each client is served on **its own thread** (`ClientHandler`), so one slow or idle client never blocks the others.
- Connected clients are stored in a `ConcurrentHashMap<Integer, ClientHandler>`, which is safe to use from several threads. This map is the **list of connected users**.
- `broadcast()` loops over the map and sends each message to every client.
- When a client sends `/quit` or its connection drops, it is removed from the map, its socket is closed, and the others are told it left.

### Client (`ChatClient`)

- Connects with `new Socket(host, port)`.
- Uses **two threads**: the main thread reads the keyboard and sends lines to the server, while a receiver thread waits for lines from the server and prints them. Two threads are needed because waiting for keyboard input and waiting for network input both block.
- The receiver is a daemon thread, so it never keeps the program alive after the user quits.

### Protocol

The two sides exchange plain text, one message per line:

- Client to server: a chat message, or a command (`/users`, `/quit`).
- Server to client: `[User N] message` for chat, `*** User N joined/left the chat ***` for events, and `Welcome...` / `Online now...` for information.

## Error handling

- If the server is not running, the client prints a clear message instead of crashing.
- If the server stops, connected clients print "Disconnected from the server." and exit.
- If a client drops without typing `/quit`, the server removes it and announces it left.

## Testing

Start the server, then open two or three client terminals. Check that a message from one client appears in all of them, that `/users` lists everyone, and that `/quit` announces the departure to the others.
