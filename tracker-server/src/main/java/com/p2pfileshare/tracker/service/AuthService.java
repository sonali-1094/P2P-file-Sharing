package com.p2pfileshare.tracker.service;

import com.p2pfileshare.tracker.entity.User;
import com.p2pfileshare.tracker.repository.UserRepository;
import com.p2pfileshare.tracker.web.ApiDtos.LoginResponse;
import com.p2pfileshare.tracker.web.ApiDtos.UserDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtService jwt;
    public AuthService(UserRepository users,PasswordEncoder passwords,JwtService jwt){this.users=users;this.passwords=passwords;this.jwt=jwt;}
    @Transactional(readOnly=true) public LoginResponse login(String email,String password){
        User user=users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT)).orElse(null);
        if(user==null||!passwords.matches(password,user.getPasswordHash()))throw new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        return new LoginResponse(jwt.issue(user),"Bearer",jwt.expirationSeconds(),new UserDto(user.getId(),user.getEmail(),user.getRole()));
    }
}
