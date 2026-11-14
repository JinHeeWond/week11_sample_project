package edu.handong.web11.controller;

import edu.handong.web11.domain.Attachment;
import edu.handong.web11.repository.AttachmentRepository;
import edu.handong.web11.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {
    private final AttachmentRepository attachments;
    private final FileStorageService storage;
    public AttachmentController(AttachmentRepository attachments, FileStorageService storage) {
        this.attachments = attachments; this.storage = storage;
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        //요청 ID로 첨부파일 메타데이터를 조회한다
        Attachment file = attachments.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일이 없습니다."));
        //저장된 이름으로 실제 파일을 찾는다.
        Resource resource = storage.load(file);
        //브라우저가 첨부파일로 다운로드하도록 Content-Disposition 값을 만든다
        String disposition = ContentDisposition.attachment()
            //원본 파일명을 UTF-8로 인코딩해서 한글 이름과 함께 다운로드 응답에 전달한다.
            .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
            //구성한 다운로드 정보를 HTTP 헤더에 넣도록 문자열로 변환한다.
            .build().toString();
        //정상 처리 상태인 HTTP 200이다.
        return ResponseEntity.ok()
            //파일 내용을 일반 바이너리 데이터로 전송한다
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            //다운로드 방식과 파일명이 담긴 Content-Disposition 헤더를 설정한다
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            //실제 파일을 나타내는 Resource를 응답으로 전달한다.
            .body(resource);
    }
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id) {
        Attachment file = attachments.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일이 없습니다."));
        Resource resource = storage.loadImage(file);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(file.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(resource);
    }
}
