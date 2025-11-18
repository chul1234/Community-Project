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

            // ★ 보완: saveFile이 "업로드 스킵"으로 빈 Map을 반환했을 때는 DB에 넣지 않기
            if (info != null && !info.isEmpty()) {
                postFileDAO.insertFile(postId, info);        
                savedFiles.add(info); 
            }
        }
    }


        return savedFiles; //저장된 정보들을 리스트에 모아서 반환
    }

    /**
     * (2) 단일 파일 저장 (디스크에만 저장, DB 작업은 호출하는 쪽에서)
     */
    @Override
    public Map<String, Object> saveFile(MultipartFile file) { 
        Map<String, Object> fileInfo = new HashMap<>();

        // ▼▼▼ [오류 수정 2] 0바이트 파일 저장을 위해 file.isEmpty() 체크 제거 ▼▼▼
        if (file == null) {
        // ▲▲▲ [오류 수정 2] ▲▲▲
            return fileInfo;
        }

        try {
            // ------------- 1) webkitRelativePath에서 폴더 경로/파일명 분리 -------------
            // [참고] 이 로직은 file.getOriginalFilename()에 "MyFolder/image.jpg" 같은
            // 경로가 포함되어 온다는 가정 하에 동작합니다.
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
     */
    @Override
    public void deleteFilesByPostId(int postId) {
        List<Map<String, Object>> files = postFileDAO.findByPostId(postId);

        // ▼▼▼ [오류 수정 1] 삭제 시 file_path 사용 ▼▼▼
        for (Map<String, Object> file : files) {
            String savedName = (String) file.get("saved_name");
            String subDir = (String) file.get("file_path"); // // 수정됨: file_path 읽기
            
            deletePhysicalFile(savedName, subDir); // // 수정됨
        }
        // ▲▲▲ [오류 수정 1] ▲▲▲

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

            // ▼▼▼ [오류 수정 1] 삭제 시 file_path 사용 ▼▼▼
            postFileDAO.findById(fileId).ifPresent(file -> {
                String savedName = (String) file.get("saved_name");
                String subDir = (String) file.get("file_path"); // // 수정됨: file_path 읽기
                
                deletePhysicalFile(savedName, subDir);   // // 수정됨: 디스크 삭제
                postFileDAO.deleteById(fileId);          // DB 삭제
            });
            // ▲▲▲ [오류 수정 1] ▲▲▲
        }
    }

    /**
     * 실제 디스크에서 파일 삭제하는 내부 헬퍼 메소드
     */
    // ▼▼▼ [오류 수정 1] subDir 파라미터 추가 ▼▼▼
    private void deletePhysicalFile(String savedName, String subDir) {
        try {
            if (savedName == null) return;
            if (subDir == null) subDir = ""; // file_path가 NULL인 경우 (기존 파일)
            
            // [수정됨] "C:/upload" + "MyFolder" -> "C:/upload/MyFolder"
            Path folderPath = Paths.get(uploadDir).resolve(subDir); // // 수정됨
            Path target = folderPath.resolve(savedName); // // 수정됨
            
            if (Files.exists(target)) {
                Files.delete(target);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // ▲▲▲ [오류 수정 1] ▲▲▲

    @Override
    public List<Map<String, Object>> getFilesByPostId(int postId) { 
        return postFileDAO.findByPostId(postId); 
    } 

    @Override
    public Map<String, Object> getFileById(int fileId) {        
        return postFileDAO.findById(fileId).orElse(null);       
    }                                                           
}