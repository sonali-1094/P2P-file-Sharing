package com.p2pfileshare.tracker.web;

import com.p2pfileshare.tracker.service.PeerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/peers") @Tag(name="Peers")
public class PeerController {
    private final PeerService peers; public PeerController(PeerService peers){this.peers=peers;}
    @PostMapping("/heartbeat") ApiDtos.PeerDto heartbeat(@Valid @RequestBody ApiDtos.HeartbeatRequest request,JwtAuthenticationToken auth,HttpServletRequest servlet){
        String ip=request.ipAddress()==null||request.ipAddress().isBlank()?servlet.getRemoteAddr():request.ipAddress();
        return peers.heartbeat(Long.parseLong(auth.getToken().getSubject()),request.peerUid(),ip,request.port());
    }
    @GetMapping("/online") List<ApiDtos.PeerDto> online(){return peers.online();}
}
