package com.example.demo.service.file.impl;

import java.io.IOException; // ★ 수정됨: 파일 메타데이터 DAO
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

    // 업로드 폴더 경로 (프로젝트 루트 기준)
    private final String uploadDir = "uploads";  // ★ 유지: properties 없이 직접 지정

    @Autowired
    private PostFileDAO postFileDAO; // ★ 수정됨: DB 연동용 DAO 주입

    /**
     * (1) 특정 게시글에 대한 여러 개 파일 저장
     * - 디스크에 저장 + post_files INSERT
     */
    @Override
    public List<Map<String, Object>> saveFilesForPost(int postId, List<MultipartFile> files) {
        List<Map<String, Object>> savedFiles = new ArrayList<>();

        if (files == null || files.isEmpty()) {
            return savedFiles;
        }

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                Map<String, Object> info = saveFile(file);   // 디스크에 저장
                postFileDAO.insertFile(postId, info);        // DB에 메타데이터 저장
                savedFiles.add(info);
            }
        }

        return savedFiles;
    }

    /**
     * (2) 단일 파일 저장 (디스크에만 저장, DB 작업은 호출하는 쪽에서)
     */
    @Override
    public Map<String, Object> saveFile(MultipartFile file) {
    Map<String, Object> fileInfo = new HashMap<>();

    if (file == null || file.isEmpty()) {
        return fileInfo;
    }

    try {
        // ------------- 1) webkitRelativePath에서 폴더 경로/파일명 분리 -------------
        String originalFullPath = file.getOriginalFilename();
        if (originalFullPath == null) originalFullPath = "unknown";

        originalFullPath = originalFullPath.replace("\\", "/");

        String filePath = "";      // DB에 넣을 폴더 경로
        String originalName = "";  // DB에 넣을 파일명

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
        Files.createDirectories(folderPath);   // ★ 폴더 존재 안 하면 자동 생성

        // 실제 파일 저장 위치
        Path savePath = folderPath.resolve(savedName);

        Files.copy(file.getInputStream(), savePath, StandardCopyOption.REPLACE_EXISTING);

        // ------------- 4) DB 저장용 정보 세팅 -------------
        fileInfo.put("original_name", originalName);
        fileInfo.put("saved_name", savedName);
        fileInfo.put("file_path", filePath);   // ★ 새로 추가한 컬럼
        fileInfo.put("content_type", file.getContentType());
        fileInfo.put("file_size", file.getSize());

        return fileInfo;

    } catch (IOException e) {
        throw new RuntimeException("파일 저장 중 오류 발생: " + file.getOriginalFilename(), e);
    }
}


    /**
     * (3) 특정 게시글에 연결된 모든 파일 삭제
     * - DB에서 파일 목록 조회 → 디스크 파일 삭제 → post_files 삭제
     */
    @Override
    public void deleteFilesByPostId(int postId) {
        // 1) 현재 게시글의 파일 목록 조회
        List<Map<String, Object>> files = postFileDAO.findByPostId(postId);

        // 2) 디스크 파일 삭제
        for (Map<String, Object> file : files) {
            Object savedNameObj = file.get("saved_name");
            if (savedNameObj instanceof String) {
                deletePhysicalFile((String) savedNameObj);
            }
        }

        // 3) DB 메타데이터 삭제
        postFileDAO.deleteByPostId(postId);
    }

    /**
     * (4) 파일 id 목록으로 선택 삭제
     * - 수정 화면에서 특정 첨부만 삭제할 때 사용
     */
    @Override
    public void deleteFilesByIds(List<Integer> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        for (Integer fileId : fileIds) {
            if (fileId == null) continue;

            // 1) 파일 메타데이터 조회
            postFileDAO.findById(fileId).ifPresent(file -> {
                Object savedNameObj = file.get("saved_name");
                if (savedNameObj instanceof String) {
                    deletePhysicalFile((String) savedNameObj);   // 디스크 삭제
                }
                postFileDAO.deleteById(fileId);                  // DB 삭제
            });
        }
    }

    /**
     * 실제 디스크에서 파일 삭제하는 내부 헬퍼 메소드
     */
    private void deletePhysicalFile(String savedName) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            Path target = uploadPath.resolve(savedName);
            if (Files.exists(target)) {
                Files.delete(target);
            }
        } catch (IOException e) {
            // 로그만 찍고, 예외는 상위로 올리지 않음 (게시글 삭제 자체는 진행)
            e.printStackTrace();
        }
    }

    @Override
    public List<Map<String, Object>> getFilesByPostId(int postId) { // ★ 추가됨
        return postFileDAO.findByPostId(postId); // ★ 추가됨
    } // ★ 추가됨

        @Override
    public Map<String, Object> getFileById(int fileId) {        // ★ 수정됨
        // PostFileDAO 의 findById(Optional) 를 사용해서 한 건 조회
        return postFileDAO.findById(fileId).orElse(null);       // ★ 수정됨
    }                                                           // ★ 수정됨


}
