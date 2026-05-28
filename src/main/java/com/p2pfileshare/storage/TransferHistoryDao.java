package com.p2pfileshare.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TransferHistoryDao {
    private final Path databasePath;

    public TransferHistoryDao(Path databasePath) throws SQLException {
        this.databasePath = databasePath;
        initialize();
    }

    private Connection connection() throws SQLException {
        String url = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        return DriverManager.getConnection(url);
    }

    private void initialize() throws SQLException {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (Exception ignored) {
        }
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS transfer_history (id INTEGER PRIMARY KEY AUTOINCREMENT, file_name TEXT NOT NULL, file_size INTEGER NOT NULL, started_at TEXT NOT NULL, finished_at TEXT, status TEXT NOT NULL)");
        }
    }

    public void save(TransferRecord record) throws SQLException {
        String sql = "INSERT INTO transfer_history (file_name, file_size, started_at, finished_at, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, record.getFileName());
            stmt.setLong(2, record.getFileSize());
            stmt.setString(3, record.getStartedAt().toString());
            stmt.setString(4, record.getFinishedAt() == null ? "" : record.getFinishedAt().toString());
            stmt.setString(5, record.getStatus());
            stmt.executeUpdate();
        }
    }

    public List<TransferRecord> findAll() throws SQLException {
        String sql = "SELECT file_name, file_size, started_at, finished_at, status FROM transfer_history ORDER BY id DESC";
        List<TransferRecord> records = new ArrayList<>();
        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                records.add(new TransferRecord(
                        rs.getString("file_name"),
                        rs.getLong("file_size"),
                        Instant.parse(rs.getString("started_at")),
                        rs.getString("finished_at").isBlank() ? null : Instant.parse(rs.getString("finished_at")),
                        rs.getString("status")
                ));
            }
        }
        return records;
    }
}
