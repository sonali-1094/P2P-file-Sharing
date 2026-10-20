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
class UserPeerRepositoryTest {
    @Autowired UserRepository users;
    @Autowired PeerRepository peers;

    @Test void findsUserAndPeers() {
        User user=users.saveAndFlush(new User("owner@example.test","hash",Role.USER));
        Instant now=Instant.now();
        Peer peer=peers.saveAndFlush(new Peer(user,"peer-1","127.0.0.1",9000,now));
        assertThat(users.findByEmail("owner@example.test")).contains(user);
        assertThat(peers.findByPeerUid("peer-1")).contains(peer);
        assertThat(peers.findByLastSeenAfter(now.minusSeconds(1))).contains(peer);
    }
}
