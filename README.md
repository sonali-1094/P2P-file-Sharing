# P2P File Share

A Java-based peer-to-peer file sharing prototype with LAN discovery, chunked transfer, JavaFX UI, and SQLite history.

## Build

Requires Java 21 and Maven.

```bash
mvn clean package
```

## Run UI mode

Use Maven to run the JavaFX app, because Maven adds the JavaFX libraries from `pom.xml`:

```bash
mvn javafx:run "-Djavafx.args=ui"
```

### UI workflow

1. Open the app on two computers on the same Wi-Fi/LAN.
2. On the sender, click **Choose File**, select a file, then click **Start Sharing**.
3. On both computers, click **Start Discovery**.
4. On the receiver, select the discovered peer, click **Refresh Files**, select a file, then click **Download Selected**.
5. Downloaded files are saved to `Downloads/P2P File Share`.

## Run CLI mode

CLI mode starts a sharing peer for one file:

```bash
mvn javafx:run "-Djavafx.args=cli /path/to/share/file"
```

## Project structure

- `network/` — peer discovery, handshake, TCP peer server
- `transfer/` — chunking and parallel download engine
- `ui/` — JavaFX UI skeleton and themes
- `storage/` — SQLite transfer history DAO
- `model/` — peer, chunk, and manifest domain objects
- `util/` — SHA-256 hashing and backoff utilities
