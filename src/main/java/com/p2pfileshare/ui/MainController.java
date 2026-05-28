package com.p2pfileshare.ui;

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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainController {
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
    private Button startDiscoveryButton;
    @FXML
    private Button toggleThemeButton;
    @FXML
    private Label statusLabel;
    @FXML
    private TextArea logOutput;
    @FXML
    private VBox rightPanel;

    private boolean darkMode = true;
    private final Map<String, FileManifest> sharedManifests = new ConcurrentHashMap<>();
    private final Map<String, Peer> peersByDisplayName = new HashMap<>();
    private final Map<String, NetworkFileManifest> remoteFilesByDisplayName = new HashMap<>();
    private final TransferEngine transferEngine = new TransferEngine();
    private PeerServer peerServer;
    private PeerDiscoveryService discoveryService;
    private int localPort = 49000;

    @FXML
    public void initialize() {
        setupChart();
        peersList.getItems().add("No peers discovered yet");
        transfersList.getItems().add("No active transfers");
        remoteFilesList.getItems().add("Select a peer and refresh files");
        statusLabel.setText("Ready");
        peersList.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, newValue) -> {
            if (newValue != null && peersByDisplayName.containsKey(newValue)) {
                onRefreshFiles();
            }
        });
    }

    @FXML
    protected void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a file to share");
        File file = chooser.showOpenDialog(peersList.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            FileManifest manifest = FileChunker.buildManifest(file.toPath());
            sharedManifests.put(manifest.getFileId(), manifest);
            transfersList.getItems().remove("No active transfers");
            transfersList.getItems().add("Sharing: " + manifest.getFileName() + " (" + readableSize(manifest.getFileSize()) + ")");
            statusLabel.setText("File ready to share");
            appendLog("Selected file for sharing: " + manifest.getFileName());
        } catch (IOException | IllegalArgumentException e) {
            statusLabel.setText("Could not share file");
            appendLog("Could not prepare file: " + e.getMessage());
        }
    }

    @FXML
    protected void onStartSharing() {
        startPeerServer();
    }

    @FXML
    protected void onStartDiscovery() {
        startPeerServer();
        if (discoveryService == null) {
            discoveryService = new PeerDiscoveryService(localPort);
            discoveryService.start();
            Thread refresher = new Thread(() -> {
                while (discoveryService != null) {
                    refreshPeers();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "ui-peer-refresh");
            refresher.setDaemon(true);
            refresher.start();
        }
        statusLabel.setText("Discovery started on port " + localPort);
        appendLog("Peer discovery started.");
    }

    @FXML
    protected void onRefreshFiles() {
        String selectedPeer = peersList.getSelectionModel().getSelectedItem();
        Peer peer = peersByDisplayName.get(selectedPeer);
        if (peer == null) {
            appendLog("Select a discovered peer first.");
            return;
        }
        statusLabel.setText("Loading files from peer...");
        transferEngine.listAvailableFiles(peer).whenComplete((files, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                statusLabel.setText("Could not load peer files");
                appendLog("Could not load files: " + rootMessage(throwable));
                return;
            }
            showRemoteFiles(files);
            statusLabel.setText("Loaded " + files.size() + " file(s)");
        }));
    }

    @FXML
    protected void onDownloadSelected() {
        String selectedPeer = peersList.getSelectionModel().getSelectedItem();
        String selectedFile = remoteFilesList.getSelectionModel().getSelectedItem();
        Peer peer = peersByDisplayName.get(selectedPeer);
        NetworkFileManifest manifest = remoteFilesByDisplayName.get(selectedFile);
        if (peer == null || manifest == null) {
            appendLog("Select a peer and a file to download.");
            return;
        }

        overallProgress.setProgress(0);
        Path downloadDirectory = Path.of(System.getProperty("user.home"), "Downloads", "P2P File Share");
        statusLabel.setText("Downloading " + manifest.fileName() + "...");
        appendLog("Downloading " + manifest.fileName() + " from " + peer.getAddress().getHostAddress());
        transferEngine.downloadFile(peer, manifest, downloadDirectory, progress ->
                Platform.runLater(() -> overallProgress.setProgress(progress))
        ).whenComplete((path, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                statusLabel.setText("Download failed");
                appendLog("Download failed: " + rootMessage(throwable));
                return;
            }
            overallProgress.setProgress(1);
            transfersList.getItems().add("Downloaded: " + path.getFileName());
            statusLabel.setText("Download complete");
            appendLog("Saved to " + path);
        }));
    }

    @FXML
    protected void onToggleTheme() {
        darkMode = !darkMode;
        speedChart.getScene().getStylesheets().clear();
        speedChart.getScene().getStylesheets().add(getClass().getResource(darkMode ? "/ui/dark.css" : "/ui/light.css").toExternalForm());
        statusLabel.setText(darkMode ? "Dark mode enabled" : "Light mode enabled");
    }

    private void setupChart() {
        NumberAxis xAxis = (NumberAxis) speedChart.getXAxis();
        NumberAxis yAxis = (NumberAxis) speedChart.getYAxis();
        xAxis.setLabel("Seconds");
        yAxis.setLabel("KB/s");
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Download speed");
        speedChart.getData().add(series);
        Platform.runLater(() -> series.getData().add(new XYChart.Data<>(0, 0)));
    }

    private void appendLog(String text) {
        Platform.runLater(() -> {
            logOutput.appendText(text + "\n");
        });
    }

    private void startPeerServer() {
        if (peerServer != null) {
            statusLabel.setText("Sharing on port " + localPort);
            return;
        }
        localPort = findAvailablePort(49000);
        peerServer = new PeerServer(localPort, sharedManifests);
        peerServer.start();
        statusLabel.setText("Sharing on port " + localPort);
        appendLog("Peer server started on port " + localPort + ".");
    }

    private void refreshPeers() {
        if (discoveryService == null) {
            return;
        }
        List<Peer> peers = discoveryService.getDiscoveredPeers().stream()
                .sorted((left, right) -> left.toString().compareToIgnoreCase(right.toString()))
                .toList();
        Platform.runLater(() -> {
            peersByDisplayName.clear();
            peersList.getItems().clear();
            if (peers.isEmpty()) {
                peersList.getItems().add("No peers discovered yet");
                return;
            }
            for (Peer peer : peers) {
                String displayName = peer.toString();
                peersByDisplayName.put(displayName, peer);
                peersList.getItems().add(displayName);
            }
        });
    }

    private void showRemoteFiles(List<NetworkFileManifest> files) {
        remoteFilesByDisplayName.clear();
        remoteFilesList.getItems().clear();
        if (files.isEmpty()) {
            remoteFilesList.getItems().add("Peer is not sharing files");
            return;
        }
        for (NetworkFileManifest file : files) {
            String displayName = file.fileName() + " (" + readableSize(file.fileSize()) + ")";
            remoteFilesByDisplayName.put(displayName, file);
            remoteFilesList.getItems().add(displayName);
        }
    }

    private int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 50; port++) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {
            }
        }
        return startPort;
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof UncheckedIOException unchecked && unchecked.getCause() != null) {
            return unchecked.getCause().getMessage();
        }
        return current.getMessage();
    }

    public void shutdown() {
        if (discoveryService != null) {
            discoveryService.stop();
            discoveryService = null;
        }
        if (peerServer != null) {
            peerServer.stop();
            peerServer = null;
        }
    }
}
