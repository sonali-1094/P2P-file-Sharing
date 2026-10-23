package com.p2pfileshare.ui;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.JsonNode;
import com.p2pfileshare.tracker.TrackerClient;

import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.model.Peer;
import com.p2pfileshare.network.NetworkFileManifest;
import com.p2pfileshare.network.PeerDiscoveryService;
import com.p2pfileshare.network.PeerServer;
import com.p2pfileshare.storage.TransferHistoryDao;
import com.p2pfileshare.storage.TransferRecord;
import com.p2pfileshare.transfer.FileChunker;
import com.p2pfileshare.transfer.TransferEngine;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

public class MainController {

    // ============================================================
    // FXML UI COMPONENTS
    // ============================================================

    @FXML
    private ListView<String> peersList;

    @FXML
    private ListView<String> transfersList;

    @FXML
    private ListView<String> remoteFilesList;

    @FXML
    private ProgressBar overallProgress;

    @FXML
    private LineChart<Number, Number> speedChart;

    @FXML
    private Label statusLabel;

    @FXML
    private Label localAddressLabel;

    @FXML
    private Label downloadFolderLabel;

    @FXML
    private TextArea logOutput;

    @FXML private Pane loginPane;
    @FXML private Pane dashboardPane;
    @FXML private Pane topBar;
    @FXML private Pane bottomPane;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label loginErrorLabel;
    @FXML private TextField fileSearchField;
    @FXML private ListView<String> onlinePeersList;
    @FXML private ListView<String> trackerTransfersList;
    @FXML private ListView<String> trackerFilesList;

    private final TrackerClient tracker = new TrackerClient();
    private final ExecutorService trackerExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread thread = new Thread(r, "tracker-http"); thread.setDaemon(true); return thread;
    });
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tracker-heartbeat"); thread.setDaemon(true); return thread;
    });
    private final Map<Long, JsonNode> trackerFilesById = new ConcurrentHashMap<>();
    private volatile boolean authenticated;

    // ============================================================
    // APPLICATION STATE
    // ============================================================

    private boolean darkMode = true;

    private final Map<String, FileManifest> sharedManifests =
            new ConcurrentHashMap<>();

    // Keyed using stable peer ID.
    private final Map<String, Peer> peersById =
            new HashMap<>();

    private final Map<String, NetworkFileManifest> remoteFilesByDisplayName =
            new HashMap<>();

    private final TransferEngine transferEngine =
            new TransferEngine();

    // One identity for this peer, shared by the server and discovery.
    private final String peerId =
            "peer-" + java.util.UUID.randomUUID();

    private PeerServer peerServer;

    private PeerDiscoveryService discoveryService;

    private ScheduledExecutorService peerRefreshExecutor;

    private int localPort = 49000;

    private Path downloadDirectory =
            Path.of(
                    System.getProperty("user.home"),
                    "Downloads",
                    "P2P File Share"
            );

    // Prevent overlapping downloads.
    private final AtomicBoolean downloadInProgress =
            new AtomicBoolean(false);

    // ------------------------------------------------------------
    // Live speed chart state (only touched on the JavaFX thread)
    // ------------------------------------------------------------

    private XYChart.Series<Number, Number> speedSeries;

    private Timeline speedTimeline;

    private double latestProgress;

    private long speedFileSize;

    private double lastSampledBytes;

    private long speedStartNanos;

    private long lastSampleNanos;

    // ------------------------------------------------------------
    // Transfer history (SQLite)
    // ------------------------------------------------------------

    private static final int HISTORY_LIMIT = 20;

    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter
                    .ofPattern("dd MMM yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

    private TransferHistoryDao historyDao;

    // ============================================================
    // INITIALIZE
    // ============================================================

    @FXML
    public void initialize() {

        setupChart();

        showLogin();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (authenticated) trackerExecutor.execute(() -> trackerCall(() -> tracker.heartbeat(peerId, localPort, null)));
        }, 0, 20, TimeUnit.SECONDS);

        trackerFilesList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
            if (value == null) return;
            try {
                long id = Long.parseLong(value.substring(0, value.indexOf(" |")));
                trackerExecutor.execute(() -> trackerCall(() -> {
                    JsonNode sources = tracker.sources(id);
                    Platform.runLater(() -> {
                        onlinePeersList.getItems().setAll();
                        sources.forEach(peer -> onlinePeersList.getItems().add(peer.path("peerUid").asText() + " @ " + peer.path("ipAddress").asText() + ":" + peer.path("port").asInt()));
                    });
                    return sources;
                }));
            } catch (RuntimeException ignored) { }
        });

        peersList.getItems().add(
                "No peers discovered yet"
        );

        transfersList.getItems().add(
                "No active transfers"
        );

        remoteFilesList.getItems().add(
                "Select a peer and refresh files"
        );

        initHistory();

        statusLabel.setText("Ready");

        localAddressLabel.setText(
                "Local endpoint: not started"
        );

        downloadFolderLabel.setText(
                "Download folder: " + downloadDirectory
        );

        peersList
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (ignored, oldValue, newValue) -> {

                            if (newValue != null
                                    && peersById.containsKey(
                                            peerIdFor(newValue))) {

                                onRefreshFiles();
                            }
                        }
                );
    }

    // ============================================================
    // CHOOSE FILE TO SHARE
    // ============================================================

    @FXML
    protected void onChooseFile() {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Choose a file to share"
        );

        File file =
                chooser.showOpenDialog(
                        peersList.getScene().getWindow()
                );

        if (file == null) {
            return;
        }

        statusLabel.setText("Preparing file metadata...");
        trackerExecutor.execute(() -> {
          try {
            FileManifest manifest = FileChunker.buildManifest(file.toPath());
            JsonNode registered = tracker.registerFile(manifest);
            Platform.runLater(() -> {

            sharedManifests.put(
                    manifest.getFileId(),
                    manifest
            );

            transfersList
                    .getItems()
                    .remove(
                            "No active transfers"
                    );

            transfersList
                    .getItems()
                    .add(
                            "Sharing: "
                                    + manifest.getFileName()
                                    + " ("
                                    + readableSize(
                                            manifest.getFileSize()
                                    )
                                    + ")"
                    );

            statusLabel.setText("Registered with tracker (ID " + registered.path("id").asLong() + ")");

            appendLog(
                    "Selected file for sharing: "
                            + manifest.getFileName()
            );

            });
          } catch (Exception e) {
            if (e instanceof TrackerClient.UnauthorizedException) reportTrackerError(e);
            else Platform.runLater(() -> {
                statusLabel.setText("Could not share file");
                appendLog("Could not prepare/register file: " + message(e));
            });
          }
        });
    }

    @FXML protected void onLogin() {
        authenticate(false);
    }

    @FXML protected void onRegister() {
        authenticate(true);
    }

    private void authenticate(boolean register) {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        char[] password = passwordField.getText() == null ? new char[0] : passwordField.getText().toCharArray();
        passwordField.clear();
        loginErrorLabel.setText(register ? "Registering..." : "Signing in...");
        trackerExecutor.execute(() -> {
            try {
                if (register) {
                    tracker.register(email, password);
                    Platform.runLater(() -> loginErrorLabel.setText("Account created. Sign in with your credentials."));
                } else {
                    tracker.login(email, password);
                    authenticated = true;
                    Platform.runLater(() -> {
                        showDashboard();
                        statusLabel.setText("Connected to tracker");
                        refreshTrackerDashboard("");
                    });
                }
            } catch (Exception e) { Platform.runLater(() -> loginErrorLabel.setText(message(e))); }
            finally { java.util.Arrays.fill(password, '\0'); }
        });
    }

    @FXML protected void onLogout() {
        authenticated = false;
        tracker.logout();
        showLogin();
    }

    @FXML protected void onSearchTrackerFiles() {
        if (authenticated) refreshTrackerDashboard(fileSearchField.getText());
    }

    @FXML protected void onStartTrackerTransfer() {
        String selected = trackerFilesList.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.contains(" |")) {
            statusLabel.setText("Select a tracker file first");
            return;
        }
        long fileId;
        try { fileId = Long.parseLong(selected.substring(0, selected.indexOf(" |"))); }
        catch (NumberFormatException e) { statusLabel.setText("Select a tracker file first"); return; }
        trackerExecutor.execute(() -> trackerCall(() -> {
            JsonNode started = tracker.createTransfer(fileId, null);
            Platform.runLater(() -> statusLabel.setText("Tracker transfer started for " + started.path("fileName").asText("selected file")));
            refreshTrackerDashboard(fileSearchField.getText());
            return started;
        }));
    }

    private void refreshTrackerDashboard(String search) {
        trackerExecutor.execute(() -> {
            try {
                JsonNode files = tracker.listFiles(search, 0, 50);
                JsonNode peers = tracker.onlinePeers();
                JsonNode transfers = tracker.transfers();
                Platform.runLater(() -> {
                    trackerFilesById.clear(); trackerFilesList.getItems().clear();
                    files.path("content").forEach(file -> {
                        long id = file.path("id").asLong(); trackerFilesById.put(id, file);
                        trackerFilesList.getItems().add(id + " | " + file.path("name").asText() + " (" + file.path("sizeBytes").asLong() + " bytes) · " + file.path("ownerEmail").asText());
                    });
                    onlinePeersList.getItems().setAll();
                    peers.forEach(peer -> onlinePeersList.getItems().add(peer.path("peerUid").asText() + " @ " + peer.path("ipAddress").asText() + ":" + peer.path("port").asInt()));
                    trackerTransfersList.getItems().setAll();
                    transfers.path("content").forEach(transfer -> trackerTransfersList.getItems().add(transfer.path("fileName").asText() + " · " + transfer.path("status").asText() + " · " + transfer.path("startedAt").asText()));
                    if (files.path("content").isEmpty()) trackerFilesList.getItems().add("No files found");
                    if (peers.isEmpty()) onlinePeersList.getItems().add("No online peers");
                    if (transfers.path("content").isEmpty()) trackerTransfersList.getItems().add("No transfers");
                });
            } catch (Exception e) { reportTrackerError(e); }
        });
    }

    private void showLogin() {
        if (loginPane == null) return;
        loginPane.setVisible(true); loginPane.setManaged(true);
        dashboardPane.setVisible(false); dashboardPane.setManaged(false);
        topBar.setVisible(false); topBar.setManaged(false);
        bottomPane.setVisible(false); bottomPane.setManaged(false);
    }

    private void showDashboard() {
        loginPane.setVisible(false); loginPane.setManaged(false);
        dashboardPane.setVisible(true); dashboardPane.setManaged(true);
        topBar.setVisible(true); topBar.setManaged(true);
        bottomPane.setVisible(true); bottomPane.setManaged(true);
    }

    @FunctionalInterface private interface TrackerCall<T> { T run() throws Exception; }
    private <T> T trackerCall(TrackerCall<T> call) {
        try { return call.run(); } catch (Exception e) { reportTrackerError(e); return null; }
    }
    private void reportTrackerError(Exception error) {
        Platform.runLater(() -> {
            if (error instanceof TrackerClient.UnauthorizedException) { authenticated = false; showLogin(); loginErrorLabel.setText(error.getMessage()); }
            else { statusLabel.setText("Tracker error"); appendLog("Tracker request failed: " + message(error)); }
        });
    }
    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    // ============================================================
    // START SHARING
    // ============================================================

    @FXML
    protected void onStartSharing() {

        startPeerServer();
    }

    // ============================================================
    // START DISCOVERY
    // ============================================================

    @FXML
    protected void onStartDiscovery() {

        startPeerServer();

        if (discoveryService == null) {

            discoveryService =
                    new PeerDiscoveryService(
                            localPort,
                            peerId
                    );

            discoveryService.start();

            peerRefreshExecutor =
                    Executors.newSingleThreadScheduledExecutor(
                            r -> {

                                Thread thread =
                                        new Thread(
                                                r,
                                                "ui-peer-refresh"
                                        );

                                thread.setDaemon(true);

                                return thread;
                            }
                    );

            peerRefreshExecutor.scheduleAtFixedRate(
                    this::refreshPeers,
                    0,
                    2,
                    TimeUnit.SECONDS
            );
        }

        statusLabel.setText(
                "Discovery started on port "
                        + localPort
        );

        appendLog(
                "Peer discovery started."
        );
    }

    // ============================================================
    // STOP
    // ============================================================

    @FXML
    protected void onStop() {

        stopDiscovery();

        if (peerServer != null) {

            peerServer.stop();

            peerServer = null;

            appendLog(
                    "Peer server stopped."
            );
        }

        localAddressLabel.setText(
                "Local endpoint: not started"
        );

        statusLabel.setText(
                "Stopped"
        );
    }

    // ============================================================
    // CHOOSE DOWNLOAD FOLDER
    // ============================================================

    @FXML
    protected void onChooseDownloadFolder() {

        DirectoryChooser chooser =
                new DirectoryChooser();

        chooser.setTitle(
                "Choose download folder"
        );

        File current =
                downloadDirectory.toFile();

        if (current.exists()) {

            chooser.setInitialDirectory(
                    current
            );
        }

        File selected =
                chooser.showDialog(
                        peersList.getScene().getWindow()
                );

        if (selected == null) {
            return;
        }

        downloadDirectory =
                selected.toPath();

        downloadFolderLabel.setText(
                "Download folder: "
                        + downloadDirectory
        );

        appendLog(
                "Download folder set to "
                        + downloadDirectory
        );
    }

    // ============================================================
    // REFRESH REMOTE FILES
    // ============================================================

    @FXML
    protected void onRefreshFiles() {

        String selectedDisplay =
                peersList
                        .getSelectionModel()
                        .getSelectedItem();

        Peer peer =
                peersById.get(
                        peerIdFor(selectedDisplay)
                );

        if (peer == null) {

            appendLog(
                    "Select a discovered peer first."
            );

            return;
        }

        statusLabel.setText(
                "Loading files from peer..."
        );

        transferEngine
                .listAvailableFiles(peer)
                .whenComplete(
                        (files, throwable) ->
                                Platform.runLater(() -> {

                                    if (throwable != null) {

                                        statusLabel.setText(
                                                "Could not load peer files"
                                        );

                                        appendLog(
                                                "Could not load files: "
                                                        + rootMessage(
                                                                throwable
                                                        )
                                        );

                                        return;
                                    }

                                    showRemoteFiles(
                                            files
                                    );

                                    statusLabel.setText(
                                            "Loaded "
                                                    + files.size()
                                                    + " file(s)"
                                    );
                                })
                );
    }

    // ============================================================
    // DOWNLOAD SELECTED FILE
    // ============================================================

    @FXML
    protected void onDownloadSelected() {

        // Prevent overlapping downloads.
        if (!downloadInProgress.compareAndSet(
                false,
                true
        )) {

            appendLog(
                    "A download is already in progress. "
                            + "Please wait for it to finish."
            );

            return;
        }

        String selectedDisplay =
                peersList
                        .getSelectionModel()
                        .getSelectedItem();

        String selectedFile =
                remoteFilesList
                        .getSelectionModel()
                        .getSelectedItem();

        Peer peer =
                peersById.get(
                        peerIdFor(selectedDisplay)
                );

        NetworkFileManifest manifest =
                remoteFilesByDisplayName.get(
                        selectedFile
                );

        if (peer == null || manifest == null) {

            appendLog(
                    "Select a peer and a file to download."
            );

            downloadInProgress.set(false);

            return;
        }

        // Reset transfer state.
        transferEngine.resume();

        overallProgress.setProgress(0);

        final Instant startedAt = Instant.now();

        startSpeedTracking(manifest.fileSize());

        statusLabel.setText(
                "Downloading "
                        + manifest.fileName()
                        + "..."
        );

        appendLog(
                "Downloading "
                        + manifest.fileName()
                        + " from "
                        + peer.getAddress().getHostAddress()
        );

        transferEngine
                .downloadFile(
                        peer,
                        manifest,
                        downloadDirectory,
                        progress ->
                                Platform.runLater(() -> {

                                    overallProgress
                                            .setProgress(progress);

                                    // Progress callbacks can arrive out
                                    // of order; never go backwards.
                                    latestProgress =
                                            Math.max(
                                                    latestProgress,
                                                    progress
                                            );
                                })
                )
                .whenComplete(
                        (path, throwable) ->
                                Platform.runLater(() -> {

                                    downloadInProgress
                                            .set(false);

                                    stopSpeedTracking();

                                    recordTransfer(
                                            manifest,
                                            startedAt,
                                            throwable == null
                                                    ? "COMPLETED"
                                                    : "FAILED"
                                    );

                                    if (throwable != null) {

                                        statusLabel.setText(
                                                "Download failed"
                                        );

                                        appendLog(
                                                "Download failed: "
                                                        + rootMessage(
                                                                throwable
                                                        )
                                        );

                                        return;
                                    }

                                    overallProgress
                                            .setProgress(1);

                                    transfersList
                                            .getItems()
                                            .add(
                                                    "Downloaded: "
                                                            + path
                                                                    .getFileName()
                                            );

                                    statusLabel.setText(
                                            "Download complete"
                                    );

                                    appendLog(
                                            "Saved to "
                                                    + path
                                    );
                                })
                );
    }

    // ============================================================
    // PAUSE DOWNLOAD
    // ============================================================

    @FXML
    protected void onPauseDownload() {

        if (!downloadInProgress.get()) {

            appendLog(
                    "No download is currently running."
            );

            return;
        }

        transferEngine.pause();

        statusLabel.setText(
                "Download paused"
        );

        appendLog(
                "Download paused."
        );
    }

    // ============================================================
    // RESUME DOWNLOAD
    // ============================================================

    @FXML
    protected void onResumeDownload() {

        if (!downloadInProgress.get()) {

            appendLog(
                    "No download is currently running."
            );

            return;
        }

        transferEngine.resume();

        statusLabel.setText(
                "Download resumed"
        );

        appendLog(
                "Download resumed."
        );
    }

    // ============================================================
    // TOGGLE THEME
    // ============================================================

    @FXML
    protected void onToggleTheme() {

        darkMode = !darkMode;

        speedChart
                .getScene()
                .getStylesheets()
                .clear();

        speedChart
                .getScene()
                .getStylesheets()
                .add(
                        getClass()
                                .getResource(
                                        darkMode
                                                ? "/ui/dark.css"
                                                : "/ui/light.css"
                                )
                                .toExternalForm()
                );

        statusLabel.setText(
                darkMode
                        ? "Dark mode enabled"
                        : "Light mode enabled"
        );
    }

    // ============================================================
    // SETUP SPEED CHART
    // ============================================================

    private void setupChart() {

        NumberAxis xAxis =
                (NumberAxis) speedChart.getXAxis();

        NumberAxis yAxis =
                (NumberAxis) speedChart.getYAxis();

        xAxis.setLabel(
                "Seconds"
        );

        yAxis.setLabel(
                "KB/s"
        );

        XYChart.Series<Number, Number> series =
                new XYChart.Series<>();

        speedSeries = series;

        series.setName(
                "Download speed"
        );

        speedChart
                .getData()
                .add(series);

        Platform.runLater(
                () ->
                        series
                                .getData()
                                .add(
                                        new XYChart.Data<>(
                                                0,
                                                0
                                        )
                                )
        );
    }

    // ============================================================
    // APPEND LOG
    // ============================================================

    private void appendLog(String text) {

        Platform.runLater(
                () ->
                        logOutput.appendText(
                                text + "\n"
                        )
        );
    }

    // ============================================================
    // START PEER SERVER
    // ============================================================

    private void startPeerServer() {

        if (peerServer != null) {

            statusLabel.setText(
                    "Sharing on port "
                            + localPort
            );

            return;
        }

        localPort =
                findAvailablePort(49000);

        peerServer =
                new PeerServer(
                        localPort,
                        sharedManifests,
                        peerId
                );

        try {

            peerServer.start();

        } catch (IOException e) {

            peerServer = null;

            statusLabel.setText(
                    "Failed to start peer server"
            );

            appendLog(
                    "Could not start peer server on port "
                            + localPort
                            + ": "
                            + e.getMessage()
            );

            return;
        }

        statusLabel.setText(
                "Sharing on port "
                        + localPort
        );

        localAddressLabel.setText(
                "Local endpoint: "
                        + localHostAddress()
                        + ":"
                        + localPort
        );

        appendLog(
                "Peer server started on port "
                        + localPort
                        + "."
        );
    }

    // ============================================================
    // REFRESH PEERS
    // ============================================================

    private void refreshPeers() {

        if (discoveryService == null) {
            return;
        }

        List<Peer> peers =
                discoveryService
                        .getDiscoveredPeers()
                        .stream()
                        .sorted(
                                (left, right) ->
                                        left.toString()
                                                .compareToIgnoreCase(
                                                        right.toString()
                                                )
                        )
                        .toList();

        Platform.runLater(
                () -> {

                    // Remember current selection.
                    String selectedDisplay =
                            peersList
                                    .getSelectionModel()
                                    .getSelectedItem();

                    String selectedPeerId =
                            peerIdFor(
                                    selectedDisplay
                            );

                    peersById.clear();

                    peersList
                            .getItems()
                            .clear();

                    if (peers.isEmpty()) {

                        peersList
                                .getItems()
                                .add(
                                        "No peers discovered yet"
                                );

                        return;
                    }

                    String toReselect = null;

                    for (Peer peer : peers) {

                        String displayName =
                                peer.toString();

                        peersById.put(
                                peer.getPeerId(),
                                peer
                        );

                        peersList
                                .getItems()
                                .add(
                                        displayName
                                );

                        if (peer
                                .getPeerId()
                                .equals(
                                        selectedPeerId
                                )) {

                            toReselect =
                                    displayName;
                        }
                    }

                    if (toReselect != null) {

                        peersList
                                .getSelectionModel()
                                .select(
                                        toReselect
                                );
                    }
                }
        );
    }

    // ============================================================
    // GET PEER ID
    // ============================================================

    private String peerIdFor(
            String displayName) {

        if (displayName == null) {
            return null;
        }

        int at =
                displayName.indexOf('@');

        return at >= 0
                ? displayName.substring(
                        0,
                        at
                )
                : displayName;
    }

    // ============================================================
    // SHOW REMOTE FILES
    // ============================================================

    private void showRemoteFiles(
            List<NetworkFileManifest> files) {

        remoteFilesByDisplayName.clear();

        remoteFilesList
                .getItems()
                .clear();

        if (files.isEmpty()) {

            remoteFilesList
                    .getItems()
                    .add(
                            "Peer is not sharing files"
                    );

            return;
        }

        for (NetworkFileManifest file : files) {

            String displayName =
                    file.fileName()
                            + " ("
                            + readableSize(
                                    file.fileSize()
                            )
                            + ")";

            remoteFilesByDisplayName.put(
                    displayName,
                    file
            );

            remoteFilesList
                    .getItems()
                    .add(
                            displayName
                    );
        }
    }

    // ============================================================
    // FIND AVAILABLE PORT
    // ============================================================

    private int findAvailablePort(
            int startPort) {

        for (
                int port = startPort;
                port < startPort + 50;
                port++
        ) {

            try (
                    ServerSocket socket =
                            new ServerSocket(port)
            ) {

                return socket.getLocalPort();

            } catch (IOException unavailable) {

                // Try next port.
            }
        }

        return startPort;
    }

    // ============================================================
    // READABLE FILE SIZE
    // ============================================================

    private String readableSize(
            long bytes) {

        if (bytes < 1024) {

            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {

            return String.format(
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                "%.1f MB",
                bytes / (1024.0 * 1024.0)
        );
    }

    // ============================================================
    // ROOT ERROR MESSAGE
    // ============================================================

    private String rootMessage(
            Throwable throwable) {

        Throwable current =
                throwable;

        while (
                current.getCause() != null
        ) {

            current =
                    current.getCause();
        }

        if (
                current instanceof UncheckedIOException unchecked
                        && unchecked.getCause() != null
        ) {

            return unchecked
                    .getCause()
                    .getMessage();
        }

        return current.getMessage();
    }

    // ============================================================
    // LOCAL HOST ADDRESS
    // ============================================================

    private String localHostAddress() {

        try {

            return InetAddress
                    .getLocalHost()
                    .getHostAddress();

        } catch (IOException e) {

            return "localhost";
        }
    }

    // ============================================================
    // STOP DISCOVERY
    // ============================================================

    private void stopDiscovery() {

        if (peerRefreshExecutor != null) {

            peerRefreshExecutor
                    .shutdownNow();

            peerRefreshExecutor = null;
        }

        if (discoveryService != null) {

            discoveryService.stop();

            discoveryService = null;

            appendLog(
                    "Peer discovery stopped."
            );
        }
    }

    // ============================================================
    // LIVE SPEED CHART
    // ============================================================

    /**
     * Starts sampling the download speed once per second.
     * Speed is calculated from the progress callback:
     * (bytes downloaded since last sample) / (seconds since last sample).
     */
    private void startSpeedTracking(long fileSize) {

        stopSpeedTracking();

        speedFileSize = fileSize;
        latestProgress = 0;
        lastSampledBytes = 0;
        speedStartNanos = System.nanoTime();
        lastSampleNanos = speedStartNanos;

        if (speedSeries != null) {

            speedSeries.getData().clear();

            speedSeries.getData().add(
                    new XYChart.Data<>(0, 0)
            );
        }

        speedTimeline = new Timeline(
                new KeyFrame(
                        Duration.seconds(1),
                        event -> sampleSpeed()
                )
        );

        speedTimeline.setCycleCount(Timeline.INDEFINITE);

        speedTimeline.play();
    }

    private void stopSpeedTracking() {

        if (speedTimeline == null) {
            return;
        }

        speedTimeline.stop();

        speedTimeline = null;

        // Final sample so the last part of the transfer is drawn.
        sampleSpeed();
    }

    private void sampleSpeed() {

        if (speedSeries == null) {
            return;
        }

        long now = System.nanoTime();

        double seconds =
                (now - lastSampleNanos) / 1_000_000_000.0;

        if (seconds <= 0) {
            return;
        }

        double bytes = latestProgress * speedFileSize;

        double kbPerSecond =
                Math.max(
                        0,
                        (bytes - lastSampledBytes)
                                / 1024.0
                                / seconds
                );

        double elapsed =
                (now - speedStartNanos) / 1_000_000_000.0;

        speedSeries.getData().add(
                new XYChart.Data<>(elapsed, kbPerSecond)
        );

        // Keep the chart readable on very long transfers.
        if (speedSeries.getData().size() > 300) {

            speedSeries.getData().remove(0);
        }

        lastSampledBytes = bytes;

        lastSampleNanos = now;
    }

    // ============================================================
    // TRANSFER HISTORY
    // ============================================================

    /**
     * Opens the SQLite history database and shows the most
     * recent transfers in the Transfers list.
     */
    private void initHistory() {

        try {

            Path dbFile =
                    Path.of(
                            System.getProperty("user.home"),
                            ".p2pfileshare",
                            "history.db"
                    );

            historyDao = new TransferHistoryDao(dbFile);

            List<TransferRecord> recent =
                    historyDao.findAll();

            if (recent.isEmpty()) {
                return;
            }

            transfersList
                    .getItems()
                    .remove("No active transfers");

            int count =
                    Math.min(recent.size(), HISTORY_LIMIT);

            // findAll() is newest-first; show oldest-first.
            for (int i = count - 1; i >= 0; i--) {

                transfersList
                        .getItems()
                        .add(describeRecord(recent.get(i)));
            }

        } catch (SQLException e) {

            historyDao = null;

            appendLog(
                    "Transfer history unavailable: "
                            + e.getMessage()
            );
        }
    }

    private String describeRecord(TransferRecord record) {

        return "History: "
                + record.getFileName()
                + " ("
                + readableSize(record.getFileSize())
                + ") - "
                + record.getStatus()
                + " - "
                + HISTORY_TIME.format(record.getStartedAt());
    }

    /**
     * Saves a finished (or failed) download without blocking
     * the JavaFX thread.
     */
    private void recordTransfer(
            NetworkFileManifest manifest,
            Instant startedAt,
            String status) {

        TransferHistoryDao dao = historyDao;

        if (dao == null) {
            return;
        }

        TransferRecord record =
                new TransferRecord(
                        manifest.fileName(),
                        manifest.fileSize(),
                        startedAt,
                        Instant.now(),
                        status
                );

        CompletableFuture.runAsync(() -> {

            try {

                dao.save(record);

            } catch (SQLException e) {

                appendLog(
                        "Could not save transfer history: "
                                + e.getMessage()
                );
            }
        });
    }

    // ============================================================
    // SHUTDOWN
    // ============================================================

    public void shutdown() {

        stopSpeedTracking();

        stopDiscovery();

        if (peerServer != null) {

            peerServer.stop();

            peerServer = null;
        }

        transferEngine.shutdown();
    }
}
