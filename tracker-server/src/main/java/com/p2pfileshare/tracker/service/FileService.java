package com.p2pfileshare.tracker.service;

import com.p2pfileshare.tracker.entity.*;
import com.p2pfileshare.tracker.repository.SharedFileRepository;
import com.p2pfileshare.tracker.repository.UserRepository;
import com.p2pfileshare.tracker.web.ApiDtos.*;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileService {
    private final SharedFileRepository files; private final UserRepository users; private final PeerService peers;
    public FileService(SharedFileRepository files,UserRepository users,PeerService peers){this.files=files;this.users=users;this.peers=peers;}
    @Transactional public SaveResult register(long ownerId,RegisterFileRequest r){
        validate(r);
        User owner=users.findById(ownerId).orElseThrow(ApiException::notFound);
        SharedFile existing=files.findByOwnerIdAndFileHash(ownerId,r.fileHash().toLowerCase(Locale.ROOT)).orElse(null);
        if(existing!=null){existing.update(r.name(),r.visibility());return new SaveResult(dto(existing,true),false);}
        SharedFile file=new SharedFile(owner,r.fileHash().toLowerCase(Locale.ROOT),r.name(),r.sizeBytes(),r.chunkSize(),r.visibility());
        for(ChunkRequest c:r.chunks())file.addChunk(new FileChunk(c.index(),c.sha256().toLowerCase(Locale.ROOT),c.sizeBytes()));
        return new SaveResult(dto(files.save(file),true),true);
    }
    private void validate(RegisterFileRequest r){
        long sum=0;for(int i=0;i<r.chunks().size();i++){
            ChunkRequest c=r.chunks().get(i);if(c.index()!=i)throw new ApiException(HttpStatus.BAD_REQUEST,"Chunk indexes must be contiguous from zero");
            if(c.sizeBytes()>r.chunkSize())throw new ApiException(HttpStatus.BAD_REQUEST,"Chunk size exceeds chunkSize");sum+=c.sizeBytes();
        }
        if(sum!=r.sizeBytes())throw new ApiException(HttpStatus.BAD_REQUEST,"Chunk sizes must sum to sizeBytes");
        if(r.sizeBytes()==0&&!r.chunks().isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"An empty file cannot contain chunks");
    }
    @Transactional(readOnly=true) public PageDto<FileDto> list(long userId,boolean admin,String search,int page,int size){
        Page<SharedFile> result=files.findVisible(userId,admin,search==null?"":search,PageRequest.of(page,Math.min(size,100),Sort.by(Sort.Direction.DESC,"createdAt")));
        return pageDto(result,false);
    }
    @Transactional(readOnly=true) public FileDto get(long id,long userId,boolean admin){return dto(visible(id,userId,admin),true);}
    @Transactional(readOnly=true) public List<PeerDto> sources(long id,long userId,boolean admin){return peers.onlineForUser(visible(id,userId,admin).getOwner().getId());}
    @Transactional public FileDto visibility(long id,long userId,boolean admin,Visibility visibility){SharedFile f=visible(id,userId,admin);if(!admin&&!f.getOwner().getId().equals(userId))throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");f.update(f.getName(),visibility);return dto(f,true);}
    @Transactional public void delete(long id,long userId,boolean admin){SharedFile f=files.findById(id).orElseThrow(ApiException::notFound);if(!admin&&!f.getOwner().getId().equals(userId))throw new ApiException(HttpStatus.NOT_FOUND,"Resource not found");files.delete(f);}
    private SharedFile visible(long id,long userId,boolean admin){SharedFile f=files.findDetailedById(id).orElseThrow(ApiException::notFound);if(f.getVisibility()==Visibility.PRIVATE&&!admin&&!f.getOwner().getId().equals(userId))throw ApiException.notFound();return f;}
    public static FileDto dto(SharedFile f,boolean chunks){return new FileDto(f.getId(),f.getName(),f.getSizeBytes(),f.getChunkSize(),f.getFileHash(),f.getVisibility(),f.getOwner().getId(),f.getOwner().getEmail(),f.getCreatedAt(),chunks?f.getChunks().stream().map(c->new ChunkDto(c.getChunkIndex(),c.getSha256(),c.getSizeBytes())).toList():List.of());}
    private static PageDto<FileDto> pageDto(Page<SharedFile> p,boolean chunks){return new PageDto<>(p.getContent().stream().map(f->dto(f,chunks)).toList(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());}
    public record SaveResult(FileDto file,boolean created){}
}
