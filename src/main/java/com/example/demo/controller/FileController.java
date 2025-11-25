package com.example.demo.controller; // 컨트롤러 클래스가 속한 패키지

// 자바 기본/IO, 경로, 인코딩 관련 클래스 import
import java.io.IOException;                         // 입출력 처리 중 발생하는 예외
import java.net.URLEncoder;                        // 파일명 등을 URL 인코딩하기 위한 유틸
import java.nio.charset.StandardCharsets;          // UTF-8 등의 문자셋 상수
import java.nio.file.Files;                        // 파일 존재 여부, MIME 타입 추론 등에 사용
import java.nio.file.Path;                         // 파일/디렉토리 경로 표현
import java.nio.file.Paths;                        // 문자열을 Path 로 변환하는 유틸
import java.util.HashMap;                          // Map 구현체로 결과 JSON 구성용
import java.util.Map;                              // 키-값 쌍 데이터 구조

import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입(@Autowired) 처리
import org.springframework.core.io.Resource;                    // 응답 바디에 실을 파일 리소스 표현
import org.springframework.core.io.UrlResource;                // 파일 시스템의 URL 기반 Resource 구현체
import org.springframework.http.HttpHeaders;                   // HTTP 응답 헤더 상수들
import org.springframework.http.MediaType;                     // Content-Type 표현
import org.springframework.http.ResponseEntity;                // HTTP 응답 전체(상태코드+헤더+바디) 표현
import org.springframework.web.bind.annotation.GetMapping;     // GET 요청 매핑 어노테이션
import org.springframework.web.bind.annotation.PathVariable;   // URL 경로 변수 바인딩 어노테이션
import org.springframework.web.bind.annotation.PostMapping;    // POST 요청 매핑 어노테이션
import org.springframework.web.bind.annotation.RequestParam;   // 쿼리스트링/폼 파라미터 바인딩 어노테이션
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러(결과를 JSON/바이너리로 반환)
import org.springframework.web.multipart.MultipartFile;         // 업로드된 파일을 표현하는 타입

import com.example.demo.service.file.IFileService; // 파일 메타데이터 조회용 서비스 인터페이스

@RestController // 이 클래스를 REST API 컨트롤러로 등록 (메서드 반환값이 곧 HTTP 응답 바디)
public class FileController {

    @Autowired // 스프링이 IFileService 구현체(FileServiceImpl)를 자동으로 주입
    private IFileService fileService;    // 첨부파일 메타데이터 조회/저장/삭제를 담당하는 서비스

    // FileServiceImpl과 동일한 업로드 루트 디렉터리 (실제 파일이 저장되는 기본 경로)
    private final String uploadDir = "C:/upload";  // 예: C:/upload/..., D:/upload/... 등으로 사용

    /**
     * 첨부파일 다운로드
     * - 브라우저가 "파일로 다운로드" 받도록 하는 API
     * - 예: GET /api/files/3/download
     */
    @GetMapping("/api/files/{fileId}/download") // /api/files/{fileId}/download URL에 대한 GET 요청 처리
    public ResponseEntity<Resource> downloadFile(@PathVariable int fileId) throws IOException {
        // @PathVariable: URL 경로의 {fileId} 값을 메서드 파라미터 fileId로 바인딩

        // 1) DB에서 파일 메타데이터 조회 (file_id 기준)
        Map<String, Object> fileMeta = fileService.getFileById(fileId);

        // 파일 메타데이터가 없으면 404 Not Found 반환
        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        // 2) 메타데이터에서 필요한 필드 꺼내기
        String savedName    = (String) fileMeta.get("saved_name");     // 서버에 실제 저장된 파일명
        String originalName = (String) fileMeta.get("original_name");  // 사용자에게 보여줄 원본 파일명
        String subDir       = (String) fileMeta.get("file_path");      // 파일이 위치한 하위 폴더 경로
        if (subDir == null) subDir = "";                               // null이면 빈 문자열로 처리

        // 3) 실제 파일 경로 생성 (uploadDir + subDir + savedName)
        Path filePath = Paths.get(uploadDir)       // C:/upload
                             .resolve(subDir)      // 예: "test1/폴더A/"
                             .resolve(savedName)   // 예: "uuid_파일명.txt"
                             .normalize();         // 경로 정규화(../ 같은 것 제거)

        // 해당 경로에 파일이 실제로 없으면 404 반환
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        // 4) 파일을 스프링 Resource 형태로 감싸기 (응답 바디로 내려주기 위함)
        Resource resource = new UrlResource(filePath.toUri());

        // 5) 다운로드용 파일명 인코딩 (한글/공백 등을 UTF-8로 브라우저가 안전하게 처리할 수 있도록)
        String encodedName = URLEncoder
                .encode(originalName, StandardCharsets.UTF_8.toString()) // UTF-8 URL 인코딩
                .replaceAll("\\+", "%20");                               // 공백은 + 대신 %20으로 치환

        // 6) ResponseEntity 를 사용해 응답 생성
        return ResponseEntity.ok() // HTTP 200 OK
                .contentType(MediaType.APPLICATION_OCTET_STREAM) // 일반 이진 파일로 응답
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,         // Content-Disposition 헤더 설정
                        "attachment; filename*=UTF-8''" + encodedName // attachment: 무조건 다운로드
                )
                .body(resource); // 실제 파일 바이트 스트림을 응답 바디로 전송
    }

    /**
     * (선택) 이미지/파일을 브라우저에서 바로 보이게 하고 싶을 때 사용하는 API
     * - 다운로드가 아니라, 브라우저 창에서 바로 열어서 보여주는 용도
     * - 예: GET /api/files/3/view
     */
    @GetMapping("/api/files/{fileId}/view") // /api/files/{fileId}/view URL에 대한 GET 요청 처리
    public ResponseEntity<Resource> viewFile(@PathVariable int fileId) throws IOException {

        // 1) DB에서 파일 메타데이터 조회
        Map<String, Object> fileMeta = fileService.getFileById(fileId);

        // 메타데이터가 없으면 404
        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        // 2) 메타데이터에서 정보 추출
        String savedName    = (String) fileMeta.get("saved_name");     // 서버에 저장된 파일명
        String originalName = (String) fileMeta.get("original_name");  // 원본 파일명
        String subDir       = (String) fileMeta.get("file_path");      // 하위 폴더 경로
        if (subDir == null) subDir = "";                               // null 보호

        // 3) 실제 파일 경로 생성
        Path filePath = Paths.get(uploadDir)
                             .resolve(subDir)
                             .resolve(savedName)
                             .normalize();
        if (!Files.exists(filePath)) {          // 파일이 존재하지 않으면
            return ResponseEntity.notFound().build(); // 404 반환
        }

        // 4) 파일 Resource 생성
        Resource resource = new UrlResource(filePath.toUri());

        // 5) Content-Type 추론 (이미지면 image/jpeg, image/png 등으로 세팅)
        String contentType = Files.probeContentType(filePath);         // OS/파일 확장자 기반 MIME 추론
        if (contentType == null) {                                     // 추론 실패 시
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;    // 기본값(바이너리)으로 처리
        }

        // 6) 응답 헤더에 사용할 파일명 인코딩
        String encodedName = URLEncoder
                .encode(originalName, StandardCharsets.UTF_8.toString())
                .replaceAll("\\+", "%20");

        // 7) inline 응답: 브라우저에서 바로 열도록 설정
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType)) // 실제 MIME 타입으로 응답
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName   // inline: 새 탭에서 열리도록
                )
                .body(resource);
    }

    // 파일 메타데이터 조회 (다운로드/뷰 진입 전에 미리 정보 보여줄 때 사용)
    // 예: GET /api/files/3/meta
    @GetMapping("/api/files/{fileId}/meta") // /api/files/{fileId}/meta URL 처리
    public ResponseEntity<Map<String, Object>> getFileMeta(
            @PathVariable int fileId) {      // URL 경로에서 fileId 값 추출

        // 1) 파일 메타데이터 조회
        Map<String, Object> fileMeta = fileService.getFileById(fileId);

        // 없으면 404
        if (fileMeta == null) {
            return ResponseEntity.notFound().build();
        }

        // 있으면 200 OK + JSON(Map)으로 그대로 반환
        return ResponseEntity.ok(fileMeta);
    }

    // =========================================================================
    // 인라인 이미지 에디터용 API (에디터에서 본문에 이미지를 삽입하기 위한 전용 엔드포인트)
    // =========================================================================

    /**
     * (인라인 이미지 업로드)
     * - 게시글 본문 안에 이미지를 삽입할 때 에디터에서 호출하는 API
     * - 파일은 C:/upload/editor 폴더에 저장
     * - 응답으로 <img src="..."> 에 들어갈 URL 을 내려줌
     */
    @PostMapping("/api/editor-images") // 에디터에서 이미지 업로드할 때 사용하는 POST 요청
    public ResponseEntity<Map<String, Object>> uploadEditorImage(
            @RequestParam("file") MultipartFile file) { // multipart/form-data 의 name="file" 파라미터를 받음

        Map<String, Object> result = new HashMap<>(); // 결과 JSON을 담을 Map

        // 1) 파일 검증: null 이거나, 전송된 파일이 실제로 비어 있는 경우
        if (file == null || file.isEmpty()) {
            result.put("success", false);                      // 성공 여부 false
            result.put("message", "파일이 비어 있습니다.");      // 에러 메시지
            return ResponseEntity.badRequest().body(result);   // HTTP 400 Bad Request 로 응답
        }

        try {
            // 2) 원본 파일명 얻기 (브라우저에서 업로드한 실제 파일 이름)
            String originalName = file.getOriginalFilename();  // 예: "테스트 이미지.png"
            if (originalName == null || originalName.isBlank()) {
                originalName = "image";                        // 파일명이 비면 기본값 사용
            }

            // 3) 파일명 정제 (sanitizing)
            //    - UUID 없이, 원본 이름을 거의 그대로 쓰되
            //    - OS/파일시스템에서 문제가 될 수 있는 문자들은 '_' 로 치환
            //    - \p{L}: 모든 언어의 문자(한글 포함), \p{N}: 숫자, 나머지 특수문자·공백 등은 '_' 처리
            String sanitizedName = originalName.replaceAll("[^\\p{L}\\p{N}._-]", "_");

            // 실제 저장에 사용할 파일명 (여기서는 정제된 이름 그대로 사용)
            String savedName = sanitizedName;

            // 4) 실제 저장 경로: C:/upload/editor
            Path editorBasePath = Paths.get(uploadDir, "editor"); // "C:/upload/editor"
            Files.createDirectories(editorBasePath);              // 디렉터리가 없으면 생성

            // editor 폴더 아래에 savedName 으로 파일 경로 구성
            Path target = editorBasePath.resolve(savedName);

            // 업로드된 파일 스트림을 target 경로에 복사 (같은 이름이 있으면 덮어쓰기)
            Files.copy(
                    file.getInputStream(),                               // 업로드된 파일 inputStream
                    target,                                              // 저장할 경로
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING    // 이미 있으면 교체
            );

            // 5) 브라우저에서 접근할 URL 구성
            //    - 이 URL은 나중에 <img src="..."> 로 그대로 사용됨
            String url = "/api/editor-images/view/" + savedName; // 예: /api/editor-images/view/테스트_이미지.png

            // 응답 JSON 구성
            result.put("success", true);          // 성공 여부
            result.put("url", url);               // 에디터에서 사용할 이미지 URL
            result.put("originalName", originalName); // 원본 파일명 정보도 함께 전달

            return ResponseEntity.ok(result);     // HTTP 200 OK + JSON 응답

        } catch (IOException e) {                 // 파일 저장 과정에서 IO 예외 발생 시
            e.printStackTrace();                  // 서버 로그에 예외 출력
            result.put("success", false);         // 실패 표시
            result.put("message", "이미지 업로드 중 오류 발생"); // 에러 메시지
            return ResponseEntity                  // HTTP 500 Internal Server Error 로 응답
                    .internalServerError()
                    .body(result);
        }
    }

    /**
     * (인라인 이미지 조회)
     * - 업로드된 에디터 이미지 파일을 브라우저에 바로 보여주는 API
     * - 게시글 본문에서는 <img src="/api/editor-images/view/{fileName}"> 형태로 사용
     */
    @GetMapping("/api/editor-images/view/{fileName}") // 에디터 이미지 뷰 요청 처리
    public ResponseEntity<Resource> viewEditorImage(
            @PathVariable String fileName) throws IOException { // URL 경로에서 파일명을 받음

        // 1) 간단한 보안 체크: 경로 조작 공격 방지 ("../" 등 사용 금지)
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            // 상대 경로 조작이 의심되는 경우 400 Bad Request 반환
            return ResponseEntity.badRequest().build();
        }

        // 2) 에디터 이미지가 저장된 기본 경로 (C:/upload/editor)
        Path editorBasePath = Paths.get(uploadDir, "editor");
        Path target = editorBasePath.resolve(fileName); // 해당 파일명에 대한 전체 경로

        // 파일이 존재하지 않으면 404 Not Found
        if (!Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }

        // 3) 실제 파일 리소스로 변환
        Resource resource = new UrlResource(target.toUri());

        // 4) Content-Type 추측 (이미지라면 image/png, image/jpeg 등으로 자동 세팅)
        String contentType = Files.probeContentType(target);     // OS/확장자 기반 MIME 타입 추론
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE; // 추론 실패 시 기본값
        }

        // 5) inline 응답 시 사용될 파일명 인코딩
        String encodedName = URLEncoder
                .encode(fileName, StandardCharsets.UTF_8) // 파일명을 UTF-8 URL 인코딩
                .replaceAll("\\+", "%20");                // 공백을 %20 으로 변환

        // 6) 최종 응답 생성 (inline 으로 브라우저에서 바로 열리도록)
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType)) // 실제 MIME 타입 세팅
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName   // inline: 브라우저에서 보여주기
                )
                .body(resource); // 실제 이미지/파일 바이트를 응답 바디로 전달
    }
}
