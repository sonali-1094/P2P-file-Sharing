package com.p2pfileshare.tracker.web;

import com.p2pfileshare.tracker.entity.TransferStatus;
import com.p2pfileshare.tracker.repository.*;
import com.p2pfileshare.tracker.service.*;
import com.p2pfileshare.tracker.web.ApiDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin") @Tag(name="Administration")
public class AdminController {
    private final UserService users;private final TransferService transfers;private final PeerService peers;private final UserRepository userRepo;private final SharedFileRepository files;private final TransferRepository transferRepo;
    public AdminController(UserService users,TransferService transfers,PeerService peers,UserRepository userRepo,SharedFileRepository files,TransferRepository transferRepo){this.users=users;this.transfers=transfers;this.peers=peers;this.userRepo=userRepo;this.files=files;this.transferRepo=transferRepo;}
    @GetMapping("/users") PageDto<UserDto> users(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){Page<UserDto> p=users.list(request(page,size));return new PageDto<>(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());}
    @GetMapping("/transfers") PageDto<TransferDto> transfers(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return transfers.all(page,size);}
    @GetMapping("/stats") StatsDto stats(){Map<TransferStatus,Long> count=new EnumMap<>(TransferStatus.class);for(TransferStatus s:TransferStatus.values())count.put(s,transferRepo.countByStatus(s));return new StatsDto(userRepo.count(),peers.onlineCount(),files.count(),files.totalSharedBytes(),count);}
    private static Pageable request(int page,int size){if(page<0||size<1)throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"Page must be non-negative and size must be positive");return PageRequest.of(page,Math.min(size,100),Sort.by(Sort.Direction.DESC,"createdAt"));}
}
