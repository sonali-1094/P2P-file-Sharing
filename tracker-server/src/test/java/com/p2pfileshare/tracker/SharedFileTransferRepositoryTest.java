package com.p2pfileshare.tracker;

import com.p2pfileshare.tracker.entity.*;
import com.p2pfileshare.tracker.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SharedFileTransferRepositoryTest {
    @Autowired UserRepository users;
    @Autowired SharedFileRepository files;
    @Autowired FileChunkRepository chunks;
    @Autowired TransferRepository transfers;

    @Test void findsRecordsAndCascadesChunkDeletion() {
        User owner=users.saveAndFlush(new User("owner@example.test","hash",Role.USER));
        User downloader=users.saveAndFlush(new User("downloader@example.test","hash",Role.USER));
        SharedFile file=new SharedFile(owner,"a".repeat(64),"sample.bin",20,10,Visibility.PUBLIC);
        file.addChunk(new FileChunk(0,"b".repeat(64),10));
        file.addChunk(new FileChunk(1,"c".repeat(64),10));
        file=files.saveAndFlush(file);
        Transfer transfer=transfers.saveAndFlush(new Transfer(file,downloader,TransferStatus.STARTED,Instant.now(),null));
        assertThat(files.findByFileHash("a".repeat(64))).contains(file);
        assertThat(files.findByOwnerId(owner.getId())).contains(file);
        assertThat(transfers.findByDownloaderId(downloader.getId())).contains(transfer);
        assertThat(chunks.count()).isEqualTo(2);
        transfers.delete(transfer);
        files.delete(file);
        files.flush();
        assertThat(chunks.count()).isZero();
        assertThat(files.findById(file.getId())).isEmpty();
    }
}
