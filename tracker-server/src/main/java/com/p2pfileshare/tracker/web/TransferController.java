package com.p2pfileshare.tracker.web;

import com.p2pfileshare.tracker.service.TransferService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/transfers") @Tag(name="Transfers")
public class TransferController {
    private final TransferService transfers; public TransferController(TransferService transfers){this.transfers=transfers;}
    @PostMapping ResponseEntity<ApiDtos.TransferDto> start(@Valid @RequestBody ApiDtos.TransferCreateRequest r,JwtAuthenticationToken auth){return ResponseEntity.status(HttpStatus.CREATED).body(transfers.start(FileController.uid(auth),FileController.admin(auth),r.fileId()));}
    @PatchMapping("/{id}") ApiDtos.TransferDto finish(@PathVariable long id,@Valid @RequestBody ApiDtos.TransferFinishRequest r,JwtAuthenticationToken auth){return transfers.finish(FileController.uid(auth),id,r.status());}
    @GetMapping ApiDtos.PageDto<ApiDtos.TransferDto> mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken auth){return transfers.mine(FileController.uid(auth),page,size);}
}
