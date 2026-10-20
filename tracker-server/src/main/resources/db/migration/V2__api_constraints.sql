ALTER TABLE shared_files
    ADD CONSTRAINT uk_shared_files_owner_hash UNIQUE (owner_id, file_hash);

CREATE INDEX idx_shared_files_created_at ON shared_files (created_at);
CREATE INDEX idx_transfers_started_at ON transfers (started_at);
