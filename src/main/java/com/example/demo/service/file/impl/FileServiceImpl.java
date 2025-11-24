// 수정됨: 게시글 삭제 시 빈 폴더도 함께 정리하도록 로직 보강

package com.example.demo.service.file.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dao.PostFileDAO;
import com.example.demo.service.file.IFileService;

@Service
public class FileServiceImpl implements IFileService {

    // 업로드 폴더 경로 (C: 또는 D: 등 원하는 경로로 설정)
    private final String uploadDir = "C:/upload";

    @Autowired
    private PostFileDAO postFileDAO; // DB 연동용 DAO 주입

    /**
     * (1) 특정 게시글에 대한 여러 개 파일 저장
     * - 디스크에 저장 + post_files INSERT
     */
    @Override
    // [수정됨] 이 코드는 폴더 업로드를 지원하지 않는 *이전* 버전입니다.
    // 폴더 업로드를 위해서는 List<String> filePaths 파라미터가 필요합니다.
    // 일단 사용자가 제공한 시그니처(saveFilesForPost(int, List<MultipartFile>))를 기반으로 수정합니다.
    public List<Map<String, Object>> saveFilesForPost(int postId, List<MultipartFile> files) {
        List<Map<String, Object>> savedFiles = new ArrayList<>();

        if (files == null) {
            return savedFiles;
        }

        for (MultipartFile file : files) {
            if (file != null) {
                Map<String, Object> info = saveFile(file);   // 디스크에 저장

                // saveFile이 "업로드 스킵"으로 빈 Map을 반환했을 때는 DB에 넣지 않기
                if (info != null && !info.isEmpty()) {
                    postFileDAO.insertFile(postId, info);
                    savedFiles.add(info);
                }
            }
        }

        return savedFiles; // 저장된 정보들을 리스트에 모아서 반환
    }

    /**
     * (2) 단일 파일 저장 (디스크에만 저장, DB 작업은 호출하는 쪽에서)
     */
    @Override
    public Map<String, Object> saveFile(MultipartFile file) {
        Map<String, Object> fileInfo = new HashMap<>();

        // 0바이트 파일도 허용하되, file 자체가 null이면 스킵
        if (file == null) {
            return fileInfo;
        }

        try {
            // ------------- 1) webkitRelativePath에서 폴더 경로/파일명 분리 -------------
            // file.getOriginalFilename()에 "MyFolder/image.jpg" 같은 경로가 포함된다고 가정
            String originalFullPath = file.getOriginalFilename();
            if (originalFullPath == null || originalFullPath.isBlank()) {
                return fileInfo; // 빈 Map 반환 -> 나중에 DB insert 전에 체크해서 거를 수 있음
            }
            originalFullPath = originalFullPath.replace("\\", "/");

            String filePath = "";       // DB에 넣을 폴더 경로
            String originalName = "";   // DB에 넣을 파일명

            int idx = originalFullPath.lastIndexOf("/");
            if (idx != -1) {
                filePath = originalFullPath.substring(0, idx + 1);   // "test8/폴더1/폴더2/"
                originalName = originalFullPath.substring(idx + 1);  // "파일명.txt"
            } else {
                filePath = "";
                originalName = originalFullPath;
            }

            // ------------- 2) 저장 파일명 생성 (UUID_원본명) -------------
            String uuid = UUID.randomUUID().toString();
            String savedName = uuid + "_" + originalName;

            // ------------- 3) 실제 저장 경로 만들기 -------------
            Path basePath = Paths.get(uploadDir);

            // 폴더가 있을 경우 서버 내 폴더까지 생성
            Path folderPath = basePath.resolve(filePath);
            Files.createDirectories(folderPath);   // 폴더 존재 안 하면 자동 생성

            // 실제 파일 저장 위치
            Path savePath = folderPath.resolve(savedName);

            Files.copy(file.getInputStream(), savePath, StandardCopyOption.REPLACE_EXISTING);

            // ------------- 4) DB 저장용 정보 세팅 -------------
            fileInfo.put("original_name", originalName);
            fileInfo.put("saved_name", savedName);
            fileInfo.put("file_path", filePath);   // 새로 추가한 컬럼
            fileInfo.put("content_type", file.getContentType());
            fileInfo.put("file_size", file.getSize());

            return fileInfo;

        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류 발생: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * (3) 특정 게시글에 연결된 모든 파일 삭제
     */
    @Override
    public void deleteFilesByPostId(int postId) {
        List<Map<String, Object>> files = postFileDAO.findByPostId(postId);

        for (Map<String, Object> file : files) {
            String savedName = (String) file.get("saved_name");
            String subDir = (String) file.get("file_path"); // file_path 읽기

            deletePhysicalFile(savedName, subDir); // 디스크 삭제 + 폴더 정리
        }

        postFileDAO.deleteByPostId(postId);
    }

    /**
     * (4) 파일 id 목록으로 선택 삭제
     */
    @Override
    public void deleteFilesByIds(List<Integer> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        for (Integer fileId : fileIds) {
            if (fileId == null) continue;

            postFileDAO.findById(fileId).ifPresent(file -> {
                String savedName = (String) file.get("saved_name");
                String subDir = (String) file.get("file_path"); // file_path 읽기

                deletePhysicalFile(savedName, subDir);   // 디스크 삭제 + 폴더 정리
                postFileDAO.deleteById(fileId);          // DB 삭제
            });
        }
    }

    /**
     * 실제 디스크에서 파일 삭제하는 내부 헬퍼 메소드
     * - 파일 삭제 후, 해당 폴더가 비어 있으면 상위 폴더까지 정리 시도
     */
    private void deletePhysicalFile(String savedName, String subDir) {
        try {
            if (savedName == null) return;
            if (subDir == null) subDir = ""; // file_path가 NULL인 경우 (기존 파일)

            Path folderPath = Paths.get(uploadDir).resolve(subDir);
            Path target = folderPath.resolve(savedName);

            // 1) 실제 파일 삭제
            if (Files.exists(target)) {
                Files.delete(target);
            }

            // 2) 폴더가 비어 있으면(실제 파일이 없으면) 상위 폴더까지 정리
            cleanupEmptyDirectories(folderPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 업로드 디렉토리 하위에서, 비어 있는 폴더들을 위로 올라가며 정리하는 헬퍼 메소드
     * - 다른 게시글에서 사용하는 파일이 남아 있으면 폴더가 비어있지 않으므로 삭제되지 않음
     * - Thumbs.db, desktop.ini 같은 숨김파일은 무시하고, 있으면 같이 삭제
     */
    private void cleanupEmptyDirectories(Path folderPath) {
        try {
            if (folderPath == null) {
                return;
            }

            Path basePath = Paths.get(uploadDir);
            Path current = folderPath;

            // uploadDir 아래에서만, 위로 한 단계씩 올라가며 비어 있으면 삭제
            while (current != null
                    && !current.equals(basePath)
                    && current.startsWith(basePath)) {

                if (!Files.isDirectory(current)) {
                    break;
                }

                // 1) 폴더 안에 실제 파일/폴더가 있는지 확인 (Thumbs.db, desktop.ini 제외)
                boolean hasRealEntries;
                try (java.util.stream.Stream<Path> entries = Files.list(current)) {
                    hasRealEntries = entries.anyMatch(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return !name.equals("thumbs.db") && !name.equals("desktop.ini");
                    });
                }

                if (hasRealEntries) {
                    // 아직 실제 컨텐츠가 있으므로 더 이상 상위 폴더 삭제 시도 중단
                    break;
                }

                // 2) 남아 있을 수 있는 Thumbs.db, desktop.ini 는 여기서 같이 삭제
                try (java.util.stream.Stream<Path> entries = Files.list(current)) {
                    entries.forEach(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        if (name.equals("thumbs.db") || name.equals("desktop.ini")) {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignore) {
                                // 숨김파일 삭제 실패해도 폴더 삭제만 시도
                            }
                        }
                    });
                }

                // 3) 완전히 비어있으니 폴더 삭제
                Files.delete(current);
                current = current.getParent();
            }
        } catch (IOException e) {
            // 폴더 정리 중 에러가 나도 게시글 삭제 전체가 죽지 않게 로그만 남김
            e.printStackTrace();
        }
    }

    @Override
    public List<Map<String, Object>> getFilesByPostId(int postId) {
        return postFileDAO.findByPostId(postId);
    }

    @Override
    public Map<String, Object> getFileById(int fileId) {
        return postFileDAO.findById(fileId).orElse(null);
    }
}

// 수정됨 끝
