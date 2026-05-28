package com.p2pfileshare;

import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.network.PeerDiscoveryService;
import com.p2pfileshare.network.PeerServer;
import com.p2pfileshare.transfer.FileChunker;
import com.p2pfileshare.ui.P2PApp;
import javafx.application.Application;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final int DEFAULT_PORT = 49000;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : "cli";
        if (mode.equals("ui")) {
            Application.launch(P2PApp.class, args);
            return;
        }

        Path sharedFile = args.length > 1 ? Paths.get(args[1]) : null;

        Map<String, FileManifest> sharedManifests = new HashMap<>();
        if (sharedFile != null) {
            FileManifest manifest = FileChunker.buildManifest(sharedFile);
            sharedManifests.put(manifest.getFileId(), manifest);
            System.out.println("Sharing file: " + manifest.getFileName() + " (" + manifest.getFileSize() + " bytes)");
        }

        PeerServer peerServer = new PeerServer(DEFAULT_PORT, sharedManifests);
        peerServer.start();
        PeerDiscoveryService discoveryService = new PeerDiscoveryService(DEFAULT_PORT);
        discoveryService.start();

        System.out.println("CLI mode started. Use Ctrl+C to exit.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            discoveryService.stop();
            peerServer.stop();
        }));

        while (true) {
            Thread.sleep(10000);
            System.out.println("Discovered peers: " + discoveryService.getDiscoveredPeers().size());
            discoveryService.getDiscoveredPeers().forEach(p -> System.out.println(" - " + p));
        }
    }
}
