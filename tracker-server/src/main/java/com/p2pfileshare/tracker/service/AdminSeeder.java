package com.p2pfileshare.tracker.service;

import com.p2pfileshare.tracker.entity.Role;
import com.p2pfileshare.tracker.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class AdminSeeder {
    @Bean ApplicationRunner seedAdmin(Environment env,UserRepository users,UserService service){return args->{
        String email=env.getProperty("ADMIN_EMAIL"),password=env.getProperty("ADMIN_PASSWORD");
        if(email!=null&&!email.isBlank()&&password!=null&&!password.isBlank()&&users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT)).isEmpty())service.create(email,password,Role.ADMIN);
    };}
}
