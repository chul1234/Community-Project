package com.example.demo.service.file;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

public interface IFileService {

    /**
     * (1) 특정 게시글에 대한 여러 개 파일 저장
     *  - 디스크에 저장 + post_files INSERT
     */
    List<Map<String, Object>> saveFilesForPost(int postId, List<MultipartFile> files); // ★ 구현체와 이름/파라미터 동일

    /**
     * (2) 단일 파일 저장 (디스크에만 저장)
     */
    Map<String, Object> saveFile(MultipartFile file);

    /**
     * (3) 특정 게시글에 연결된 모든 파일 삭제
     */
    void deleteFilesByPostId(int postId);

    /**
     * (4) 파일 id 목록으로 선택 삭제 (수정 시 일부 첨부만 삭제)
     */
    void deleteFilesByIds(List<Integer> fileIds);

    /**
     * (5) 특정 게시글의 첨부 파일 목록 조회
     */
    List<Map<String, Object>> getFilesByPostId(int postId); // ★ 추가됨: 게시글별 첨부파일 목록 조회

    // com.example.demo.service.file.IFileService
    Map<String, Object> getFileById(int fileId);

}
