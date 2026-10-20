package com.p2pfileshare.tracker.web;

import com.p2pfileshare.tracker.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}
    public record RegisterRequest(@NotBlank @Email String email,@NotBlank @Size(min=8) String password) {}
    public record LoginRequest(@NotBlank @Email String email,@NotBlank String password) {}
    public record UserDto(Long id,String email,Role role) {}
    public record LoginResponse(String token,String tokenType,long expiresInSeconds,UserDto user) {}
    public record HeartbeatRequest(@NotBlank @Size(max=100) String peerUid,@Min(1) @Max(65535) int port,@Size(max=45) String ipAddress) {}
    public record PeerDto(String peerUid,String ipAddress,int port,Long ownerId,String ownerEmail,Instant lastSeen) {}
    public record ChunkRequest(@Min(0) int index,@NotBlank @Pattern(regexp="(?i)[0-9a-f]{64}") String sha256,@Min(0) int sizeBytes) {}
    public record RegisterFileRequest(@NotBlank @Size(max=255) String name,@PositiveOrZero long sizeBytes,@Positive int chunkSize,
        @NotBlank @Pattern(regexp="(?i)[0-9a-f]{64}") String fileHash,@NotNull Visibility visibility,@NotNull @Valid List<ChunkRequest> chunks) {}
    public record FileDto(Long id,String name,long sizeBytes,int chunkSize,String fileHash,Visibility visibility,Long ownerId,String ownerEmail,Instant createdAt,List<ChunkDto> chunks) {}
    public record ChunkDto(int index,String sha256,int sizeBytes) {}
    public record VisibilityRequest(@NotNull Visibility visibility) {}
    public record TransferCreateRequest(@NotNull Long fileId,String sourcePeerUid) {}
    public record TransferFinishRequest(@NotNull TransferStatus status) {}
    public record TransferDto(Long id,Long fileId,String fileName,Long downloaderId,TransferStatus status,Instant startedAt,Instant finishedAt) {}
    public record StatsDto(long users,long onlinePeers,long files,long totalSharedBytes,java.util.Map<TransferStatus,Long> transfersByStatus) {}
    public record PageDto<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
}
