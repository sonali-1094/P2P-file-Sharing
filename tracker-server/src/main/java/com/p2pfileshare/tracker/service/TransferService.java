package com.p2pfileshare.tracker.service;

import com.p2pfileshare.tracker.entity.*;
import com.p2pfileshare.tracker.repository.SharedFileRepository;
import com.p2pfileshare.tracker.repository.TransferRepository;
import com.p2pfileshare.tracker.repository.UserRepository;
import com.p2pfileshare.tracker.web.ApiDtos.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private final TransferRepository transfers; private final SharedFileRepository files; private final UserRepository users; private final FileService fileService;
    public TransferService(TransferRepository transfers,SharedFileRepository files,UserRepository users,FileService fileService){this.transfers=transfers;this.files=files;this.users=users;this.fileService=fileService;}
    @Transactional public TransferDto start(long userId,boolean admin,Long fileId){
        SharedFile f=files.findDetailedById(fileId).orElseThrow(ApiException::notFound);
        FileService.dto(f,false);
        if(f.getVisibility()==Visibility.PRIVATE&&!admin&&!f.getOwner().getId().equals(userId))throw ApiException.notFound();
        User downloader=users.findById(userId).orElseThrow(ApiException::notFound);
        return dto(transfers.save(new Transfer(f,downloader,TransferStatus.STARTED,Instant.now(),null)));
    }
    @Transactional public TransferDto finish(long userId,long transferId,TransferStatus status){
        if(status==TransferStatus.STARTED)throw new ApiException(HttpStatus.BAD_REQUEST,"Transfer status must be COMPLETED or FAILED");
        Transfer t=transfers.findById(transferId).orElseThrow(ApiException::notFound);
        if(!t.getDownloader().getId().equals(userId))throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");
        if(t.getStatus()!=TransferStatus.STARTED)throw ApiException.conflict("A finished transfer cannot be changed");
        t.finish(status,Instant.now());return dto(t);
    }
    @Transactional(readOnly=true) public PageDto<TransferDto> mine(long id,int page,int size){return page(transfers.findByDownloaderId(id,request(page,size)));}
    @Transactional(readOnly=true) public PageDto<TransferDto> all(int page,int size){return page(transfers.findAll(request(page,size)));}
    private static Pageable request(int page,int size){if(page<0||size<1)throw new ApiException(HttpStatus.BAD_REQUEST,"Page must be non-negative and size must be positive");return PageRequest.of(page,Math.min(size,100),Sort.by(Sort.Direction.DESC,"startedAt"));}
    private static PageDto<TransferDto> page(Page<Transfer> p){return new PageDto<>(p.getContent().stream().map(TransferService::dto).toList(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());}
    private static TransferDto dto(Transfer t){return new TransferDto(t.getId(),t.getFile().getId(),t.getFile().getName(),t.getDownloader().getId(),t.getStatus(),t.getStartedAt(),t.getFinishedAt());}
}
