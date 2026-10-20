package com.p2pfileshare.tracker.web;

import com.p2pfileshare.tracker.service.AuthService;
import com.p2pfileshare.tracker.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @Tag(name="Authentication")
public class AuthController {
    private final UserService users; private final AuthService auth;
    public AuthController(UserService users,AuthService auth){this.users=users;this.auth=auth;}
    @PostMapping("/register") ResponseEntity<ApiDtos.UserDto> register(@Valid @RequestBody ApiDtos.RegisterRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(users.register(request.email(),request.password()));}
    @PostMapping("/login") ApiDtos.LoginResponse login(@Valid @RequestBody ApiDtos.LoginRequest request){return auth.login(request.email(),request.password());}
}
