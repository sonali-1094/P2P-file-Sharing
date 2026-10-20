package com.p2pfileshare.tracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtSettings.class)
public class SecurityConfig {
    @Bean SecretKey jwtKey(JwtSettings settings){return new SecretKeySpec(settings.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256");}
    @Bean ObjectMapper apiObjectMapper(){return new ObjectMapper().findAndRegisterModules();}
    @Bean JwtEncoder jwtEncoder(SecretKey key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
    @Bean JwtDecoder jwtDecoder(SecretKey key){return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();}
    @Bean org.springframework.security.crypto.password.PasswordEncoder passwordEncoder(){return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();}
    @Bean JwtAuthenticationConverter jwtAuthenticationConverter(){
        JwtGrantedAuthoritiesConverter roles=new JwtGrantedAuthoritiesConverter(); roles.setAuthoritiesClaimName("role"); roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(roles); return converter;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter, AuthenticationEntryPoint entryPoint, AccessDeniedHandler denied, @org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
        return http.csrf(csrf->csrf.disable()).cors(c->c.configurationSource(cors)).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e->e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/api/health","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)).authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)).build();
    }
    @Bean CorsConfigurationSource corsConfigurationSource(org.springframework.core.env.Environment env){
        CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(List.of(env.getProperty("app.cors.allowed-origins","http://localhost:5173").split(",")));
        c.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("Authorization","Content-Type")); c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**",c); return s;
    }
    @Bean AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper){return (req,res,e)->write(mapper,req,res,HttpStatus.UNAUTHORIZED,e instanceof BadCredentialsException?"Invalid email or password":"Authentication required");}
    @Bean AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper){return (req,res,e)->write(mapper,req,res,HttpStatus.FORBIDDEN,"Access denied");}
    private static void write(ObjectMapper mapper,HttpServletRequest req,HttpServletResponse res,HttpStatus status,String message)throws IOException{
        res.setStatus(status.value());res.setContentType("application/json");mapper.writeValue(res.getOutputStream(),new ApiError(Instant.now(),status.value(),status.getReasonPhrase(),message,req.getRequestURI(),List.of()));
    }
}
