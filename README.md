# P2P File Share

A Java-based peer-to-peer file sharing prototype with LAN discovery, chunked transfer, JavaFX UI, and SQLite history.

## Build

Requires Java 21 and Maven.

Build and test everything:

```bash
mvn clean install
```

## Run UI mode

Run the desktop app:

```bash
mvn -pl desktop-ui javafx:run
```

After changing `p2p-core` code, run `mvn install -DskipTests` before running the UI.

### UI workflow

### UI workflow

1. Open the app on two computers on the same Wi-Fi/LAN.
2. On the sender, click **Choose File**, select a file, then click **Start Sharing**.
3. On both computers, click **Start Discovery**.
4. On the receiver, select the discovered peer, click **Refresh Files**, select a file, then click **Download Selected**.
5. Downloaded files are saved to `Downloads/P2P File Share`.

## Project structure

- p2p-core/ — peer discovery, chunked transfer engine, hashing, storage, models, and core tests
- desktop-ui/ — JavaFX desktop client, FXML layout, and themes




