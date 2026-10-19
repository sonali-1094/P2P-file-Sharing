package com.p2pfileshare;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.network.PeerDiscoveryService;
import com.p2pfileshare.network.PeerServer;
import com.p2pfileshare.transfer.FileChunker;
import com.p2pfileshare.ui.P2PApp;

import javafx.application.Application;

public class Main {

    private static final int DEFAULT_PORT = 49000;

    public static void main(String[] args) throws Exception {

        // Default mode is now JavaFX UI.
        // CLI mode can still be started by passing "cli" as an argument.
        String mode = args.length > 0 ? args[0].toLowerCase() : "ui";

        // =========================
        // JAVA FX UI MODE
        // =========================
        if (mode.equals("ui")) {
            Application.launch(P2PApp.class, args);
            return;
        }

        // =========================
        // CLI MODE
        // =========================

        Path sharedFile = args.length > 1
                ? Paths.get(args[1])
                : null;

        Map<String, FileManifest> sharedManifests =
                new ConcurrentHashMap<>();

        // If a file is supplied in CLI mode, create its manifest
        // and make it available for sharing.
        if (sharedFile != null) {

            FileManifest manifest =
                    FileChunker.buildManifest(sharedFile);

            sharedManifests.put(
                    manifest.getFileId(),
                    manifest
            );

            System.out.println(
                    "Sharing file: "
                            + manifest.getFileName()
                            + " ("
                            + manifest.getFileSize()
                            + " bytes)"
            );
        }

        // One identity for this peer, shared by the server and discovery.
        String peerId = "peer-" + UUID.randomUUID();

        // =========================
        // START PEER SERVER
        // =========================

        PeerServer peerServer =
                new PeerServer(
                        DEFAULT_PORT,
                        sharedManifests,
                        peerId
                );

        peerServer.start();

        // =========================
        // START PEER DISCOVERY
        // =========================

        PeerDiscoveryService discoveryService =
                new PeerDiscoveryService(DEFAULT_PORT, peerId);

        discoveryService.start();

        // =========================
        // STATUS REPORTER
        // =========================

        CountDownLatch shutdownSignal =
                new CountDownLatch(1);

        ScheduledExecutorService statusExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> new Thread(
                                r,
                                "peer-status-reporter"
                        )
                );

        statusExecutor.scheduleAtFixedRate(
                () -> {

                    System.out.println(
                            "Discovered peers: "
                                    + discoveryService
                                    .getDiscoveredPeers()
                                    .size()
                    );

                    discoveryService
                            .getDiscoveredPeers()
                            .forEach(
                                    p -> System.out.println(
                                            " - " + p
                                    )
                            );

                },
                10,
                10,
                TimeUnit.SECONDS
        );

        System.out.println(
                "CLI mode started. Use Ctrl+C to exit."
        );

        // =========================
        // SHUTDOWN HOOK
        // =========================

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {

                    statusExecutor.shutdownNow();

                    discoveryService.stop();

                    peerServer.stop();

                    shutdownSignal.countDown();

                })
        );

        // Keep CLI application running.
        shutdownSignal.await();
    }
}