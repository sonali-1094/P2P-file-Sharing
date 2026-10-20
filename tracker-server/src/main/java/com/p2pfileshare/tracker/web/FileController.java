package com.p2pfileshare.tracker.web;

import com.p2pfileshare.tracker.service.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/files") @Tag(name="Files")
public class FileController {
    private final FileService files; public FileController(FileService files){this.files=files;}
    @PostMapping ResponseEntity<ApiDtos.FileDto> register(@Valid @RequestBody ApiDtos.RegisterFileRequest request,JwtAuthenticationToken auth){
        var saved=files.register(uid(auth),request);return ResponseEntity.status(saved.created()?HttpStatus.CREATED:HttpStatus.OK).body(saved.file());
    }
    @GetMapping ApiDtos.PageDto<ApiDtos.FileDto> list(@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken auth){return files.list(uid(auth),admin(auth),search,page,size);}
    @GetMapping("/{id}") ApiDtos.FileDto get(@PathVariable long id,JwtAuthenticationToken auth){return files.get(id,uid(auth),admin(auth));}
    @GetMapping("/{id}/sources") List<ApiDtos.PeerDto> sources(@PathVariable long id,JwtAuthenticationToken auth){return files.sources(id,uid(auth),admin(auth));}
    @PatchMapping("/{id}/visibility") ApiDtos.FileDto visibility(@PathVariable long id,@Valid @RequestBody ApiDtos.VisibilityRequest request,JwtAuthenticationToken auth){return files.visibility(id,uid(auth),admin(auth),request.visibility());}
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable long id,JwtAuthenticationToken auth){files.delete(id,uid(auth),admin(auth));return ResponseEntity.noContent().build();}
    static long uid(JwtAuthenticationToken a){return Long.parseLong(a.getToken().getSubject());}
    static boolean admin(JwtAuthenticationToken a){return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN"));}
}
