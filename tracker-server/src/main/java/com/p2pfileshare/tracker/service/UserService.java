package com.p2pfileshare.tracker.service;

import com.p2pfileshare.tracker.entity.Role;
import com.p2pfileshare.tracker.entity.User;
import com.p2pfileshare.tracker.repository.UserRepository;
import com.p2pfileshare.tracker.web.ApiDtos.UserDto;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users; private final PasswordEncoder passwords;
    public UserService(UserRepository users,PasswordEncoder passwords){this.users=users;this.passwords=passwords;}
    @Transactional public UserDto register(String email,String password){return dto(create(email,password,Role.USER));}
    @Transactional public User create(String email,String password,Role role){
        String normalized=email.trim().toLowerCase(Locale.ROOT);
        if(users.findByEmail(normalized).isPresent())throw ApiException.conflict("Email already exists");
        return users.save(new User(normalized,passwords.encode(password),role));
    }
    @Transactional(readOnly=true) public UserDto getDto(long id){return users.findById(id).map(UserService::dto).orElseThrow(ApiException::notFound);}
    @Transactional(readOnly=true) public Page<UserDto> list(Pageable p){return users.findAll(p).map(UserService::dto);}
    public static UserDto dto(User u){return new UserDto(u.getId(),u.getEmail(),u.getRole());}
}
