package com.p2pfileshare.tracker.service;

import com.p2pfileshare.tracker.entity.Peer;
import com.p2pfileshare.tracker.entity.User;
import com.p2pfileshare.tracker.repository.PeerRepository;
import com.p2pfileshare.tracker.repository.UserRepository;
import com.p2pfileshare.tracker.web.ApiDtos.PeerDto;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeerService {
    private final PeerRepository peers; private final UserRepository users; private final long onlineWindow;
    public PeerService(PeerRepository peers,UserRepository users,@Value("${app.peers.online-window-seconds:30}") long onlineWindow){this.peers=peers;this.users=users;this.onlineWindow=onlineWindow;}
    @Transactional public PeerDto heartbeat(long userId,String peerUid,String ip,int port){
        User user=users.findById(userId).orElseThrow(ApiException::notFound); Peer peer=peers.findByPeerUid(peerUid).orElse(null);
        if(peer!=null){if(!peer.getUser().getId().equals(userId))throw ApiException.conflict("Peer UID is already owned by another user");peer.refresh(ip,port,Instant.now());}
        else peer=peers.save(new Peer(user,peerUid,ip,port,Instant.now()));
        return dto(peer);
    }
    @Transactional(readOnly=true) public List<PeerDto> online(){return peers.findByLastSeenAfter(Instant.now().minusSeconds(onlineWindow)).stream().map(PeerService::dto).toList();}
    @Transactional(readOnly=true) public List<PeerDto> onlineForUser(long id){return peers.findByUserIdAndLastSeenAfter(id,Instant.now().minusSeconds(onlineWindow)).stream().map(PeerService::dto).toList();}
    @Transactional(readOnly=true) public long onlineCount(){return peers.countByLastSeenAfter(Instant.now().minusSeconds(onlineWindow));}
    private static PeerDto dto(Peer p){return new PeerDto(p.getPeerUid(),p.getIpAddress(),p.getPort(),p.getUser().getId(),p.getUser().getEmail(),p.getLastSeen());}
}
