package com.p2pfileshare.tracker.service;

import com.p2pfileshare.tracker.config.JwtSettings;
import com.p2pfileshare.tracker.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder; private final JwtDecoder decoder; private final JwtSettings settings;
    public JwtService(JwtEncoder encoder,JwtDecoder decoder,JwtSettings settings){this.encoder=encoder;this.decoder=decoder;this.settings=settings;}
    public String issue(User user){
        Instant now=Instant.now(); JwtClaimsSet claims=JwtClaimsSet.builder().issuer("p2p-tracker").issuedAt(now).expiresAt(now.plusSeconds(expirationSeconds())).subject(user.getId().toString()).id(UUID.randomUUID().toString()).claim("email",user.getEmail()).claim("role",user.getRole().name()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    public long expirationSeconds(){return Math.multiplyExact(settings.expirationMinutes(),60);}
    public Jwt decode(String token){return decoder.decode(token);}
}
