package edu.handong.web11.service;

import edu.handong.web11.domain.Attachment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "pdf", "txt");
    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir}") String path) throws IOException {
        uploadDir = Path.of(path).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    public Attachment store(MultipartFile file) {
        // 내용이 없는 파일은 저장할 수 없으므로 먼저 거부한다.
        if (file.isEmpty()) throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        // 사용자가 올린 원본 파일명을 가져와 검증과 다운로드 이름 보관에 사용한다.
        String original = file.getOriginalFilename();
        // 이름이 없거나 경로 구분자, 상위 경로 표현이 포함된 파일명은 거부한다.
        if (original == null || original.isBlank() || original.contains("/") || original.contains("\\") || original.contains(".."))
            throw new IllegalArgumentException("잘못된 파일 이름입니다.");
        // 지나치게 긴 파일명이 저장 메타데이터에 들어가지 않도록 길이를 제한한다.
        if (original.length() > 240) throw new IllegalArgumentException("파일 이름이 너무 깁니다.");
        // 마지막 점을 기준으로 확장자가 시작되는 위치를 찾는다.
        int dot = original.lastIndexOf('.');
        // 확장자가 없으면 빈 문자열로 처리하고, 있으면 대소문자 차이 없이 검사하도록 소문자로 바꾼다.
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        // 허용 목록에 없는 확장자는 업로드하지 못하게 한다.
        if (!ALLOWED.contains(ext)) throw new IllegalArgumentException("허용 형식: jpg, jpeg, png, gif, pdf, txt");
        // 디스크에 쓰기 전에 파일 크기가 10MB 이하인지 확인한다.
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("파일은 10MB 이하만 가능합니다.");
        // 같은 원본 이름의 파일이 충돌할 가능성을 낮추도록 UUID에 확장자를 붙여 저장 이름을 만든다.
        String stored = UUID.randomUUID() + "." + ext;
        // 업로드 폴더와 저장 이름을 결합하고, 경로의 점과 상위 경로 표현을 정리한다.
        Path target = uploadDir.resolve(stored).normalize();
        // 정리된 저장 경로가 업로드 폴더 밖으로 벗어나면 저장을 거부한다.
        if (!target.startsWith(uploadDir)) throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        // 파일 내용을 읽는 스트림을 열고, 처리가 끝나면 자동으로 닫는다.
        try (InputStream input = file.getInputStream()) {
            // 업로드 내용을 디스크에 복사한다. 대상 파일이 이미 있으면 교체한다.
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            // Teaching sample: MIME type is inferred by extension, not trusted from request headers.
            // 요청의 MIME 헤더 대신 확장자로 타입을 정한다. 실제 파일 바이트를 검사하는 것은 아니다.
            String contentType = switch (ext) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "pdf" -> "application/pdf";
                default -> "text/plain";
            };
            // DB에 저장할 원본 이름, 저장 이름, 타입, 크기를 담은 Entity를 반환한다. 여기서 DB 저장은 하지 않는다.
            return new Attachment(original, stored, contentType, file.getSize());
        // 파일 입출력 중 실패하면 남아 있을 수 있는 파일을 정리하고 오류 응답을 만든다.
        } catch (IOException e) {
            // 불완전하게 저장된 파일의 삭제를 시도하되, 삭제 실패가 원래 저장 오류를 덮지 않게 한다.
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            // 파일 저장 실패를 HTTP 500 오류로 전달한다.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 실패", e);
        }
    }

    public Resource load(Attachment attachment) {
        Path target = uploadDir.resolve(attachment.getStoredFilename()).normalize();
        if (!target.startsWith(uploadDir) || !Files.isRegularFile(target))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 없습니다.");
        return new PathResource(target);
    }

    public Resource loadImage(Attachment attachment) {
        String name = attachment.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> null;
        };
        if (expectedType == null || !expectedType.equals(attachment.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "이미지 미리보기는 JPG, JPEG, PNG, GIF 파일만 가능합니다.");
        }
        return load(attachment);
    }

    public void deleteIfExists(Attachment attachment) {
        try {
            Files.deleteIfExists(uploadDir.resolve(attachment.getStoredFilename()).normalize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제 실패", e);
        }
    }
}
