package com.example.demo.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.file.IFileService;

@RestController
public class FileController {

    @Autowired
    private IFileService fileService;    // 첨부파일 서비스

    // ★ 수정됨: @Value("${file.upload-dir}") 제거, FileServiceImpl 과 동일하게 하드코딩 경로 사용
    private final String uploadDir = "uploads";  // 프로젝트 루트 기준 uploads 폴더  // ★ 수정됨

    /**
     * 첨부파일 다운로드
     * 예: GET /api/files/3/download
     */
    @GetMapping("/api/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable int fileId) throws IOException {

        // 1) DB에서 파일 메타데이터 조회 (file_id 기준)
        //    - 메서드 이름은 네가 실제로 구현한 이름에 맞게 바꿔야 함
        Map<String, Object> fileMeta = fileService.getFileById(fileId);  // ★ 여기 메서드명은 프로젝트에 맞게

        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        String savedName    = (String) fileMeta.get("saved_name");     // 서버에 저장된 파일명
        String originalName = (String) fileMeta.get("original_name");  // 사용자가 올린 원래 파일명

        // 2) 실제 파일 경로 만들기
        Path filePath = Paths.get(uploadDir).resolve(savedName).normalize();
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        // 3) 파일명 인코딩 (한글 깨짐 방지)
        String encodedName = URLEncoder.encode(originalName, StandardCharsets.UTF_8.toString())
                                       .replaceAll("\\+", "%20");

        // 4) 헤더 세팅 (다운로드 되도록 attachment)
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    /**
     * (선택) 이미지/파일을 브라우저에서 바로 보이게 하고 싶을 때
     * 예: GET /api/files/3/view
     */
    @GetMapping("/api/files/{fileId}/view")
    public ResponseEntity<Resource> viewFile(@PathVariable int fileId) throws IOException {

        Map<String, Object> fileMeta = fileService.getFileById(fileId);   // ★ 동일

        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        String savedName    = (String) fileMeta.get("saved_name");
        String originalName = (String) fileMeta.get("original_name");

        Path filePath = Paths.get(uploadDir).resolve(savedName).normalize();
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        // MIME 타입 추론 (이미지면 image/png, image/jpeg 등으로 나옴)
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String encodedName = URLEncoder.encode(originalName, StandardCharsets.UTF_8.toString())
                                       .replaceAll("\\+", "%20");

        // inline 이라서 브라우저에서 바로 열림 (이미지는 화면, pdf는 브라우저 뷰어 등)
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
