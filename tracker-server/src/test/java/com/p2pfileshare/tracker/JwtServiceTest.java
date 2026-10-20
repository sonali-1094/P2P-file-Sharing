package com.p2pfileshare.tracker;

import com.p2pfileshare.tracker.config.JwtSettings;
import com.p2pfileshare.tracker.entity.*;
import com.p2pfileshare.tracker.service.JwtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private static final byte[] SECRET="test-unit-secret-that-has-more-than-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static JwtEncoder encoder(byte[] bytes){return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(bytes,"HmacSHA256")));}
    private static JwtDecoder decoder(byte[] bytes){return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes,"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();}
    private static JwtService service(byte[] bytes){return new JwtService(encoder(bytes),decoder(bytes),new JwtSettings(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),60));}
    private static User user(){User u=new User("jwt@example.test","unused",Role.USER);try{var id=User.class.getDeclaredField("id");id.setAccessible(true);id.set(u,7L);return u;}catch(ReflectiveOperationException e){throw new AssertionError(e);}}

    @Test void issuesAndDecodesValidToken(){JwtService jwt=service(SECRET);String token=jwt.issue(user());assertThat(jwt.decode(token).getClaimAsString("email")).isEqualTo("jwt@example.test");assertThat(jwt.decode(token).getClaimAsString("role")).isEqualTo("USER");}
    @Test void rejectsExpiredToken(){String token=encoder(SECRET).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().subject("1").issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(60)).build())).getTokenValue();assertThatThrownBy(()->decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);}
    @Test void rejectsTamperedToken(){String token=service(SECRET).issue(user());String tampered=token.substring(0,token.length()-2)+"aa";assertThatThrownBy(()->decoder(SECRET).decode(tampered)).isInstanceOf(JwtException.class);}
    @Test void rejectsTokenSignedByWrongSecret(){String token=service(SECRET).issue(user());byte[] other="a-different-secret-which-is-long-enough".getBytes(java.nio.charset.StandardCharsets.UTF_8);assertThatThrownBy(()->decoder(other).decode(token)).isInstanceOf(JwtException.class);}
}
