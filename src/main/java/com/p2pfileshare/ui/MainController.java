package com.p2pfileshare.ui;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.model.Peer;
import com.p2pfileshare.network.NetworkFileManifest;
import com.p2pfileshare.network.PeerDiscoveryService;
import com.p2pfileshare.network.PeerServer;
import com.p2pfileshare.transfer.FileChunker;
import com.p2pfileshare.transfer.TransferEngine;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

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

    // ============================================================
    // INITIALIZE
    // ============================================================

    @FXML
    public void initialize() {

        setupChart();

        peersList.getItems().add(
                "No peers discovered yet"
        );

        transfersList.getItems().add(
                "No active transfers"
        );

        remoteFilesList.getItems().add(
                "Select a peer and refresh files"
        );

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

        try {

            FileManifest manifest =
                    FileChunker.buildManifest(
                            file.toPath()
                    );

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

            statusLabel.setText(
                    "File ready to share"
            );

            appendLog(
                    "Selected file for sharing: "
                            + manifest.getFileName()
            );

        } catch (
                IOException |
                IllegalArgumentException e
        ) {

            statusLabel.setText(
                    "Could not share file"
            );

            appendLog(
                    "Could not prepare file: "
                            + e.getMessage()
            );
        }
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
                            localPort
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
                                Platform.runLater(
                                        () -> overallProgress
                                                .setProgress(
                                                        progress
                                                )
                                )
                )
                .whenComplete(
                        (path, throwable) ->
                                Platform.runLater(() -> {

                                    downloadInProgress
                                            .set(false);

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
                        sharedManifests
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
    // SHUTDOWN
    // ============================================================

    public void shutdown() {

        stopDiscovery();

        if (peerServer != null) {

            peerServer.stop();

            peerServer = null;
        }

        transferEngine.shutdown();
    }
}