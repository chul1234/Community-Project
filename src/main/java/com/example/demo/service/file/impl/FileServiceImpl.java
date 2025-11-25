// 수정됨: 게시글 삭제 시 빈 폴더도 함께 정리하도록 로직 보강

package com.example.demo.service.file.impl; // 패키지 선언

import java.io.IOException; // 입출력 예외 처리용 예외 클래스
import java.nio.file.Files; // 파일 및 디렉토리 조작을 위한 유틸리티 클래스
import java.nio.file.Path; // 파일/디렉토리 경로 표현 클래스
import java.nio.file.Paths; // 문자열 경로를 Path 객체로 변환하는 유틸리티
import java.nio.file.StandardCopyOption; // 파일 복사 시 옵션(덮어쓰기 등)을 지정하기 위한 enum
import java.util.ArrayList; // List 구현체 ArrayList 사용
import java.util.HashMap; // Map 구현체 HashMap 사용
import java.util.List; // List 인터페이스
import java.util.Map; // Map 인터페이스
import java.util.UUID; // 고유한 파일명을 만들기 위한 UUID 클래스

import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위한 어노테이션
import org.springframework.stereotype.Service; // Service 계층을 나타내는 어노테이션
import org.springframework.web.multipart.MultipartFile; // 업로드된 파일을 표현하는 스프링 타입

import com.example.demo.dao.PostFileDAO; // 게시글 첨부파일 정보 DB 연동 DAO
import com.example.demo.service.file.IFileService; // 파일 서비스 인터페이스

@Service // 스프링 빈 등록 (서비스 컴포넌트)
public class FileServiceImpl implements IFileService { // IFileService 구현체 정의 시작

    // 업로드 폴더 경로 (C: 또는 D: 등 원하는 경로로 설정)
    private final String uploadDir = "C:/upload"; // 실제 파일이 저장될 최상위 경로

    @Autowired
    private PostFileDAO postFileDAO; // DB 연동용 DAO 주입 (post_files 테이블 접근)

    /**
     * (1) 특정 게시글에 대한 여러 개 파일 저장
     * - 디스크에 저장 + post_files INSERT
     */
    @Override
    // 이 메서드는 게시글 하나(postId)에 대해 여러 MultipartFile을 저장하고 DB에 기록함
    public List<Map<String, Object>> saveFilesForPost(int postId, List<MultipartFile> files) {
        List<Map<String, Object>> savedFiles = new ArrayList<>(); // 저장된 파일 정보를 담을 리스트

        if (files == null) { // 업로드된 파일 리스트가 null인 경우
            return savedFiles; // 비어 있는 리스트 반환 (아무 것도 저장 안 함)
        }

        for (MultipartFile file : files) { // 업로드된 파일들을 하나씩 순회
            if (file != null) { // 파일 객체가 null이 아닌 경우에만 처리
                Map<String, Object> info = saveFile(file);   // 디스크에 저장하고 파일 정보 Map 반환

                // saveFile이 "업로드 스킵"으로 빈 Map을 반환했을 때는 DB에 넣지 않기
                if (info != null && !info.isEmpty()) { // 실제로 저장된 파일 정보가 있는 경우에만
                    postFileDAO.insertFile(postId, info); // 해당 게시글 ID와 함께 post_files 테이블에 INSERT
                    savedFiles.add(info); // 호출자에게 돌려줄 리스트에도 추가
                }
            }
        }

        return savedFiles; // 저장된 파일 정보(Map)의 목록 반환
    }

    /**
     * (2) 단일 파일 저장 (디스크에만 저장, DB 작업은 호출하는 쪽에서)
     */
    @Override
    public Map<String, Object> saveFile(MultipartFile file) {
        Map<String, Object> fileInfo = new HashMap<>(); // 파일 정보를 담을 Map (DB insert용 데이터)

        // 0바이트 파일도 허용하되, file 자체가 null이면 스킵
        if (file == null) { // MultipartFile 객체 자체가 없는 경우
            return fileInfo; // 빈 Map 반환 (호출부에서 isEmpty()로 판단 가능)
        }

        try {
            // ------------- 1) webkitRelativePath에서 폴더 경로/파일명 분리 -------------
            // file.getOriginalFilename()에 "MyFolder/image.jpg" 같은 경로가 포함된다고 가정
            String originalFullPath = file.getOriginalFilename(); // 업로드 시 원본 파일명(경로 포함 가능) 획득
            if (originalFullPath == null || originalFullPath.isBlank()) { // 파일명이 비어 있거나 null인 경우
                return fileInfo; // 빈 Map 반환 → 나중에 DB insert 전에 체크해서 거를 수 있음
            }
            originalFullPath = originalFullPath.replace("\\", "/"); // 윈도우 경로 구분자(\)를 슬래시(/)로 통일

            String filePath = "";       // DB에 넣을 폴더 경로 (예: "test8/폴더1/")
            String originalName = "";   // DB에 넣을 실제 파일명 (예: "파일명.txt")

            int idx = originalFullPath.lastIndexOf("/"); // 마지막 슬래시 위치 찾기 (폴더/파일 구분)
            if (idx != -1) { // 슬래시가 존재하면 (폴더 경로가 포함된 형태)
                filePath = originalFullPath.substring(0, idx + 1);   // "test8/폴더1/폴더2/" 부분
                originalName = originalFullPath.substring(idx + 1);  // "파일명.txt" 부분
            } else { // 슬래시가 없다면 (폴더 없이 파일명만 있는 형태)
                filePath = ""; // 루트(업로드 폴더 바로 아래)에 저장
                originalName = originalFullPath; // 전체를 파일명으로 사용
            }

            // ------------- 2) 저장 파일명 생성 (UUID_원본명) -------------
            String uuid = UUID.randomUUID().toString(); // 고유한 식별자(UUID) 생성
            String savedName = uuid + "_" + originalName; // 디스크에 저장할 파일명 (충돌 방지)

            // ------------- 3) 실제 저장 경로 만들기 -------------
            Path basePath = Paths.get(uploadDir); // 최상위 업로드 디렉토리를 Path로 변환

            // 폴더가 있을 경우 서버 내 폴더까지 생성
            Path folderPath = basePath.resolve(filePath); // basePath + filePath 로 실제 폴더 경로 구성
            Files.createDirectories(folderPath);   // 폴더가 없으면 계층 구조까지 모두 생성

            // 실제 파일 저장 위치
            Path savePath = folderPath.resolve(savedName); // 폴더 경로 + 저장 파일명으로 최종 파일 경로 구성

            Files.copy(file.getInputStream(), savePath, StandardCopyOption.REPLACE_EXISTING); // 업로드 스트림을 해당 경로로 복사 (기존 파일 있으면 덮어쓰기)

            // ------------- 4) DB 저장용 정보 세팅 -------------
            fileInfo.put("original_name", originalName); // 원본 파일명
            fileInfo.put("saved_name", savedName); // 디스크에 저장된 실제 파일명(UUID 포함)
            fileInfo.put("file_path", filePath);   // 폴더 경로 (예: "test8/폴더1/")
            fileInfo.put("content_type", file.getContentType()); // MIME 타입 (image/jpeg 등)
            fileInfo.put("file_size", file.getSize()); // 파일 크기 (바이트 단위)

            return fileInfo; // 파일 정보 Map 반환

        } catch (IOException e) { // 파일 복사/디렉토리 생성 중 예외가 발생한 경우
            throw new RuntimeException("파일 저장 중 오류 발생: " + file.getOriginalFilename(), e); // 런타임 예외로 감싸서 상위로 전달
        }
    }

    /**
     * (3) 특정 게시글에 연결된 모든 파일 삭제
     */
    @Override
    public void deleteFilesByPostId(int postId) {
        List<Map<String, Object>> files = postFileDAO.findByPostId(postId); // 해당 게시글에 연결된 모든 파일 정보 조회

        for (Map<String, Object> file : files) { // 각 파일 정보에 대해 순회
            String savedName = (String) file.get("saved_name"); // 디스크에 저장된 파일명
            String subDir = (String) file.get("file_path"); // file_path (하위 폴더 경로)

            deletePhysicalFile(savedName, subDir); // 디스크에서 파일 삭제 + 폴더 정리까지 수행
        }

        postFileDAO.deleteByPostId(postId); // DB에서 해당 게시글의 첨부파일 레코드 전체 삭제
    }

    /**
     * (4) 파일 id 목록으로 선택 삭제
     */
    @Override
    public void deleteFilesByIds(List<Integer> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) { // 삭제할 파일 ID 목록이 없으면
            return; // 바로 종료
        }

        for (Integer fileId : fileIds) { // ID 목록을 순회
            if (fileId == null) continue; // null ID는 건너뜀

            // ID로 파일 정보를 조회한 뒤, 존재하면 처리
            postFileDAO.findById(fileId).ifPresent(file -> { // Optional이 값이 있을 때만 람다 실행
                String savedName = (String) file.get("saved_name"); // 디스크 파일명
                String subDir = (String) file.get("file_path"); // 폴더 경로

                deletePhysicalFile(savedName, subDir);   // 디스크에서 실제 파일 삭제 + 폴더 정리
                postFileDAO.deleteById(fileId);          // DB에서 해당 파일 레코드 삭제
            });
        }
    }

    /**
     * 실제 디스크에서 파일 삭제하는 내부 헬퍼 메소드
     * - 파일 삭제 후, 해당 폴더가 비어 있으면 상위 폴더까지 정리 시도
     */
    private void deletePhysicalFile(String savedName, String subDir) {
        try {
            if (savedName == null) return; // 저장 파일명이 없으면 아무 작업도 하지 않음
            if (subDir == null) subDir = ""; // file_path가 NULL인 경우, 루트 경로로 처리

            Path folderPath = Paths.get(uploadDir).resolve(subDir); // base 업로드 경로 + 하위 폴더 경로
            Path target = folderPath.resolve(savedName); // 실제 삭제 대상 파일 경로

            // 1) 실제 파일 삭제
            if (Files.exists(target)) { // 파일이 실제로 존재하면
                Files.delete(target); // 파일 삭제
            }

            // 2) 폴더가 비어 있으면(실제 파일이 없으면) 상위 폴더까지 정리
            cleanupEmptyDirectories(folderPath); // 해당 폴더부터 위로 올라가며 비어 있는 폴더 정리

        } catch (IOException e) { // 파일 삭제 도중 예외 발생 시
            e.printStackTrace(); // 로그에 스택 트레이스 출력 (서비스 전체는 죽지 않도록 함)
        }
    }

    /**
     * 업로드 디렉토리 하위에서, 비어 있는 폴더들을 위로 올라가며 정리하는 헬퍼 메소드
     * - 다른 게시글에서 사용하는 파일이 남아 있으면 폴더가 비어있지 않으므로 삭제되지 않음
     * - Thumbs.db, desktop.ini 같은 숨김파일은 무시하고, 있으면 같이 삭제
     */
    private void cleanupEmptyDirectories(Path folderPath) {
        try {
            if (folderPath == null) { // 인자로 받은 경로가 null이면
                return; // 아무 작업도 하지 않고 종료
            }

            Path basePath = Paths.get(uploadDir); // 최상위 업로드 디렉토리 경로
            Path current = folderPath; // 현재 검사/삭제 대상 폴더 경로

            // uploadDir 아래에서만, 위로 한 단계씩 올라가며 비어 있으면 삭제
            while (current != null
                    && !current.equals(basePath) // 최상위 업로드 폴더까지는 삭제하지 않음
                    && current.startsWith(basePath)) { // uploadDir 하위에 속한 경로인지 확인

                if (!Files.isDirectory(current)) { // 디렉토리가 아니라면 더 이상 처리하지 않음
                    break; // 루프 종료
                }

                // 1) 폴더 안에 실제 파일/폴더가 있는지 확인 (Thumbs.db, desktop.ini 제외)
                boolean hasRealEntries; // 실제 컨텐츠 존재 여부 플래그
                try (java.util.stream.Stream<Path> entries = Files.list(current)) { // 현재 폴더의 하위 항목 나열
                    hasRealEntries = entries.anyMatch(path -> { // 하나라도 "실제" 항목이 있는지 검사
                        String name = path.getFileName().toString().toLowerCase(); // 파일/폴더 이름 소문자로 변환
                        return !name.equals("thumbs.db") && !name.equals("desktop.ini"); // 숨김파일 2종을 제외한 나머지 존재 여부
                    });
                }

                if (hasRealEntries) { // 실제 파일/폴더가 하나라도 있으면
                    // 아직 실제 컨텐츠가 있으므로 더 이상 상위 폴더 삭제 시도 중단
                    break; // 루프 종료
                }

                // 2) 남아 있을 수 있는 Thumbs.db, desktop.ini 는 여기서 같이 삭제
                try (java.util.stream.Stream<Path> entries = Files.list(current)) { // 폴더 내용을 다시 순회
                    entries.forEach(path -> { // 각 항목에 대해 처리
                        String name = path.getFileName().toString().toLowerCase(); // 이름 소문자화
                        if (name.equals("thumbs.db") || name.equals("desktop.ini")) { // 숨김파일 2종이면
                            try {
                                Files.deleteIfExists(path); // 있으면 삭제
                            } catch (IOException ignore) {
                                // 숨김파일 삭제 실패해도 폴더 삭제만 시도 (무시)
                            }
                        }
                    });
                }

                // 3) 완전히 비어있으니 폴더 삭제
                Files.delete(current); // 현재 폴더 삭제
                current = current.getParent(); // 상위 폴더로 한 단계 이동하여 계속 검사
            }
        } catch (IOException e) { // 폴더 정리 중 에러가 난 경우
            // 폴더 정리 중 에러가 나도 게시글 삭제 전체가 죽지 않게 로그만 남김
            e.printStackTrace(); // 에러 정보를 콘솔에 출력
        }
    }

    @Override
    public List<Map<String, Object>> getFilesByPostId(int postId) {
        return postFileDAO.findByPostId(postId); // 특정 게시글에 연결된 파일 목록을 DB에서 조회하여 반환
    }

    @Override
    public Map<String, Object> getFileById(int fileId) {
        return postFileDAO.findById(fileId).orElse(null); // 파일 ID로 조회 후 Optional에서 값 또는 null 반환
    }
}

// 수정됨 끝
