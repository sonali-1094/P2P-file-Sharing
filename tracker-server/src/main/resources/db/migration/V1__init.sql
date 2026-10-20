CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE peers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    peer_uid VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    port INT NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    CONSTRAINT pk_peers PRIMARY KEY (id),
    CONSTRAINT uk_peers_peer_uid UNIQUE (peer_uid),
    CONSTRAINT fk_peers_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_peers_last_seen (last_seen),
    INDEX idx_peers_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shared_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    chunk_size INT NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_shared_files PRIMARY KEY (id),
    CONSTRAINT fk_shared_files_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    INDEX idx_shared_files_file_hash (file_hash),
    INDEX idx_shared_files_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE file_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    size_bytes INT NOT NULL,
    CONSTRAINT pk_file_chunks PRIMARY KEY (id),
    CONSTRAINT fk_file_chunks_file FOREIGN KEY (file_id) REFERENCES shared_files (id) ON DELETE CASCADE,
    CONSTRAINT uk_file_chunks_file_index UNIQUE (file_id, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_id BIGINT NOT NULL,
    downloader_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    CONSTRAINT pk_transfers PRIMARY KEY (id),
    CONSTRAINT fk_transfers_file FOREIGN KEY (file_id) REFERENCES shared_files (id),
    CONSTRAINT fk_transfers_downloader FOREIGN KEY (downloader_id) REFERENCES users (id),
    INDEX idx_transfers_downloader_id (downloader_id),
    INDEX idx_transfers_file_status (file_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
