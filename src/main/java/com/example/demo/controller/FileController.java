package com.example.demo.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
// import java.util.UUID;  // 더 이상 사용하지 않으므로 삭제됨

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.service.file.IFileService;

@RestController
public class FileController {

    @Autowired
    private IFileService fileService;    // 첨부파일 서비스

    // [참고] FileServiceImpl과 동일하게 C:/upload 또는 D:/upload 등으로 설정
    private final String uploadDir = "C:/upload";  // 프로젝트 루트 기준 uploads 폴더

    /**
     * 첨부파일 다운로드 (기존 기능 유지)
     * 예: GET /api/files/3/download
     */
    @GetMapping("/api/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable int fileId) throws IOException {

        Map<String, Object> fileMeta = fileService.getFileById(fileId);

        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        String savedName    = (String) fileMeta.get("saved_name");
        String originalName = (String) fileMeta.get("original_name");
        String subDir       = (String) fileMeta.get("file_path"); // file_path 읽기
        if (subDir == null) subDir = "";

        // [수정됨] 하위 폴더 경로 포함
        Path filePath = Paths.get(uploadDir).resolve(subDir).resolve(savedName).normalize();
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        String encodedName = URLEncoder.encode(originalName, StandardCharsets.UTF_8.toString())
                                       .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    /**
     * (선택) 이미지/파일을 브라우저에서 바로 보이게 하고 싶을 때 (기존 기능 유지)
     * 예: GET /api.files/3/view
     */
    @GetMapping("/api/files/{fileId}/view")
    public ResponseEntity<Resource> viewFile(@PathVariable int fileId) throws IOException {

        Map<String, Object> fileMeta = fileService.getFileById(fileId);

        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        String savedName    = (String) fileMeta.get("saved_name");
        String originalName = (String) fileMeta.get("original_name");
        String subDir       = (String) fileMeta.get("file_path"); // file_path 읽기
        if (subDir == null) subDir = "";

        // [수정됨] 하위 폴더 경로 포함
        Path filePath = Paths.get(uploadDir).resolve(subDir).resolve(savedName).normalize();
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String encodedName = URLEncoder.encode(originalName, StandardCharsets.UTF_8.toString())
                                       .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    // 파일 메타데이터 조회 (다운로드 미리보기용)
    // 예: GET /api/files/3/meta
    @GetMapping("/api/files/{fileId}/meta")
    public ResponseEntity<Map<String, Object>> getFileMeta(
            @PathVariable int fileId) {

        Map<String, Object> fileMeta = fileService.getFileById(fileId);

        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(fileMeta);
    }

    // =========================================================================
    // ▼▼▼ [신규 추가] 인라인 이미지 에디터용 API (3단계) ▼▼▼
    // =========================================================================

    /**
     * (인라인 이미지 업로드)
     * - 본문 안에 이미지를 넣을 때 호출하는 API
     * - 파일은 C:/upload/editor 폴더에 저장
     * - 응답으로 <img src="..."> 에 들어갈 URL 을 내려줌
     */
    @PostMapping("/api/editor-images")
    public ResponseEntity<Map<String, Object>> uploadEditorImage(
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> result = new HashMap<>();

        // 1) 파일 검증 -------------------------------------------------
        if (file == null || file.isEmpty()) {
            result.put("success", false);
            result.put("message", "파일이 비어 있습니다.");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            // 2) 원본 파일명 처리 -------------------------------------
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                originalName = "image";
            }

            // ★★★ [수정됨] UUID 제거, "원본 파일명" 그대로 사용 (약간의 정제만) ★★★
            // 공백이나 한글/특수문자는 간단히 '_' 로 치환해서 파일시스템 문제만 피함
            String sanitizedName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_"); // 수정됨
            String savedName = sanitizedName; // 수정됨

            // 4) 실제 저장 경로: C:/upload/editor ---------------------
            Path editorBasePath = Paths.get(uploadDir, "editor");
            Files.createDirectories(editorBasePath);

            Path target = editorBasePath.resolve(savedName);
            Files.copy(file.getInputStream(),
                       target,
                       java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 5) 브라우저에서 접근할 URL 구성 --------------------------
            //    (이 URL은 textarea 에 들어갈 <img src=""> 에 사용됨)
            String url = "/api/editor-images/view/" + savedName; // 수정됨: UUID 없이

            result.put("success", true);
            result.put("url", url);
            result.put("originalName", originalName);

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "이미지 업로드 중 오류 발생");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * (인라인 이미지 조회)
     * - 업로드된 에디터 이미지 파일을 브라우저에 바로 보여주는 API
     * - <img src="/api/editor-images/view/{fileName}"> 형태로 사용됨
     */
    @GetMapping("/api/editor-images/view/{fileName}")
    public ResponseEntity<Resource> viewEditorImage(
            @PathVariable String fileName) throws IOException {

        // 간단한 보안 체크: 경로 조작 방지 ("../" 등) -------------------
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path editorBasePath = Paths.get(uploadDir, "editor");
        Path target = editorBasePath.resolve(fileName);

        if (!Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(target.toUri());

        // Content-Type 추측 (이미지면 image/png 등으로) ---------------
        String contentType = Files.probeContentType(target);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // inline: 브라우저에서 바로 보여주기 ---------------------------
        String encodedName = URLEncoder
                .encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
