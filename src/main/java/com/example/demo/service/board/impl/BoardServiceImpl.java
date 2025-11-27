package com.example.demo.service.board.impl; // 게시판 서비스 구현 클래스가 속한 패키지

// 필요한 클래스 import
import java.nio.file.Files;        // 파일 존재 여부 확인, 삭제 등 파일 시스템 작업 유틸리티
import java.nio.file.Path;         // 파일/디렉토리 경로를 표현하는 타입
import java.nio.file.Paths;        // 문자열 → Path 객체로 변환하는 유틸리티
import java.util.ArrayList;        // List 구현체(ArrayList) 사용을 위한 import
import java.util.HashMap;          // Map 구현체(HashMap) 사용을 위한 import
import java.util.HashSet;          // Set 구현체(HashSet) 사용을 위한 import
import java.util.List;             // List 인터페이스
import java.util.Map;              // Map 인터페이스
import java.util.Set;              // Set 인터페이스
import java.util.regex.Matcher;    // 정규식 매칭 결과를 순회하기 위한 Matcher
import java.util.regex.Pattern;    // 정규식을 표현하기 위한 Pattern

import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위한 @Autowired
import org.springframework.stereotype.Service;                  // 서비스 계층을 나타내는 @Service
import org.springframework.web.multipart.MultipartFile;        // 업로드된 파일을 표현하는 타입

import com.example.demo.dao.BoardDAO;                 // 게시판 관련 DB 접근을 담당하는 DAO
import com.example.demo.service.board.IBoardService;  // 게시판 서비스 인터페이스
import com.example.demo.service.file.IFileService;    // 첨부파일 저장/삭제를 담당하는 파일 서비스 인터페이스

@Service // 스프링 서비스 계층 컴포넌트로 등록
public class BoardServiceImpl implements IBoardService { // IBoardService를 구현하는 게시판 서비스 구현체

    @Autowired // 스프링이 BoardDAO 타입의 Bean을 자동으로 주입
    private BoardDAO boardDAO; // 게시글/댓글 등 게시판 관련 DB 작업을 수행하는 DAO

    @Autowired // 스프링이 IFileService 구현체(FileServiceImpl)를 자동으로 주입
    private IFileService fileService; // 첨부파일 저장/삭제를 담당하는 서비스

    // editor 인라인 이미지가 저장된 기본 경로 (FileController 의 uploadDir 과 동일해야 함)
    private final String uploadDir = "C:/upload"; // 에디터 이미지 및 파일들이 실제 저장되는 루트 디렉토리

    /**
     * 특정 페이지의 게시글 목록과 전체 페이지 정보를 조회하는 메소드
     * @param page          요청 페이지 번호 (1부터 시작)
     * @param size          페이지당 게시글 수
     * @param searchType    검색 타입 (title, content, title_content 등)
     * @param searchKeyword 검색어
     * @return posts, totalPages, totalItems, currentPage 를 담은 Map
     */
    @Override // 인터페이스 IBoardService에 선언된 메소드를 구현
    public Map<String, Object> getAllPosts(int page, int size, String searchType, String searchKeyword) {

        // 1. offset 계산 (몇 개를 건너뛸지). page는 1부터 시작하므로 (page-1)*size
        int offset = (page - 1) * size; // 예: page=1 → 0, page=2 → size, page=3 → 2*size

        // 2. DAO를 통해 해당 페이지의 게시글 목록 조회 (검색 조건 포함)
        //    size: 가져올 개수, offset: 건너뛸 개수, searchType/searchKeyword: 검색 조건
        List<Map<String, Object>> posts = boardDAO.findAll(size, offset, searchType, searchKeyword);

        // --------------------------------------------------------------------
        // 게시글 목록에 파일/이미지/폴더 정보를 포함시킴 (목록에서 아이콘/썸네일 표시용)
        // --------------------------------------------------------------------
        for (Map<String, Object> post : posts) { // 조회된 각 게시글에 대해 반복

            int postId = (int) post.get("post_id"); // 게시글 PK(post_id)를 int로 꺼냄

            // 첨부파일 목록 조회 (post_files 테이블 기준)
            List<Map<String, Object>> files = fileService.getFilesByPostId(postId);

            // ==========================
            // 1) 첨부파일이 전혀 없을 때
            //    → 에디터(본문) 이미지가 있으면 그걸 썸네일로 사용
            //    → 그마저도 없으면 기존처럼 'NONE' 처리
            // ==========================
            if (files == null || files.isEmpty()) { // 첨부파일이 하나도 없는 경우
                post.put("fileCount", 0); // 첨부파일 수 0

                // 본문(content)에 들어있는 에디터 이미지(<img src="/api/editor-images/view/...">) 추출
                String content = (String) post.get("content");
                List<String> editorImages = extractEditorImageFileNames(content);

                if (editorImages != null && !editorImages.isEmpty()) {
                    // 첫 번째 에디터 이미지를 썸네일로 사용
                    String savedName = editorImages.get(0); // UUID_원본명 형태
                    post.put("fileType", "IMAGE"); // 목록에서는 IMAGE로 취급
                    post.put("thumbUrl", "/api/editor-images/view/" + savedName); // 에디터 이미지 뷰 URL
                } else {
                    // 에디터 이미지도 없으면 기존 로직 유지 (NONE → board-list 에서 '-')
                    post.put("fileType", "NONE");  // 파일 타입 없음
                    post.put("thumbUrl", null);    // 썸네일 URL 없음
                }

                continue; // 다음 게시글로 넘어감
            }

            // 첨부파일이 1개 이상이면 개수 저장
            post.put("fileCount", files.size()); // 목록에서 "첨부 N개" 식으로 표시할 때 사용

            // ----------------------------------------------------------------
            // 폴더 업로드인지 여부 확인 (file_path 기준)
            //   - file_path가 비어있지 않은 파일이 하나라도 있으면 "폴더 첨부"로 간주
            //   - 폴더가 하나라도 있으면 기존처럼 폴더 아이콘 우선
            // ----------------------------------------------------------------
            boolean hasFolder = false;   // 폴더 업로드 여부 플래그
            String topFolderName = null; // 최상위 폴더 이름 (목록에 표시할 이름)

            for (Map<String, Object> f : files) {            // 첨부파일들을 순회하면서
                Object pathObj = f.get("file_path");         // file_path 컬럼 값 읽기
                if (pathObj == null) {
                    continue;                                // null이면 폴더 정보 없음 → 건너뜀
                }
                String path = pathObj.toString();            // 문자열로 변환
                if (path.isBlank()) {
                    continue;                                // 빈 문자열이면 폴더 정보 없음 → 건너뜀
                }

                hasFolder = true;                            // 하나라도 경로가 있으면 폴더 업로드로 간주

                // "test용/하위/..." 형태에서 최상위 폴더명만 추출
                String normalized = path.replace("\\", "/"); // 윈도우 경로 구분자를 슬래시로 통일
                if (!normalized.endsWith("/")) {             // 끝에 슬래시가 없다면
                    normalized = normalized + "/";           // 강제로 슬래시를 덧붙여 형태 통일
                }
                int idx = normalized.indexOf('/');           // 첫 번째 슬래시 위치 찾기
                if (idx != -1) {                             // 슬래시가 존재한다면
                    topFolderName = normalized.substring(0, idx); // 첫 슬래시 앞 부분이 최상위 폴더명
                } else {
                    topFolderName = normalized;              // 슬래시가 없으면 전체를 폴더명으로 사용
                }
                break;                                       // 하나만 확인해도 충분하므로 루프 종료
            }

            if (hasFolder) { // 폴더 업로드로 판단된 경우
                // 목록에서는 폴더 아이콘으로 표시하고, 썸네일 대신 폴더명을 넘겨줌
                post.put("fileType", "FOLDER");        // 폴더 첨부 타입
                post.put("folderName", topFolderName); // 최상위 폴더 이름
                post.put("thumbUrl", null);            // 개별 썸네일 없음
                continue;                              // 이미지/파일 판별 로직은 건너뜀
            }

            // ----------------------------------------------------------------
            // 여기부터는 기존 "첫 번째 파일 기준으로 IMAGE / FILE 판단" 로직
            // (첨부가 있는 경우에는 기존 규칙 유지: 이미지 첨부 있으면 그걸 썸네일로, 아니면 파일 아이콘)
            // ----------------------------------------------------------------

            // 첫 번째 첨부파일 기준으로 타입 판단
            Map<String, Object> file = files.get(0); // 첫 번째 첨부파일 정보

            String contentType = (String) file.get("content_type"); // MIME 타입 (image/jpeg 등)

            // file_id 추출 (정수로 변환)
            Object fileIdObj = file.get("file_id");                 // file_id 컬럼 값(Object) 꺼냄
            int fileId = (fileIdObj instanceof Number)              // Number 타입이면
                    ? ((Number) fileIdObj).intValue()               // 그대로 정수로 변환
                    : Integer.parseInt(fileIdObj.toString());       // 그 외에는 문자열로 바꾼 뒤 파싱

            // 이미지 여부 판단
            if (contentType != null && contentType.startsWith("image")) { // contentType이 image로 시작하면
                post.put("fileType", "IMAGE"); // 이미지 타입으로 표시
                // 저장 파일명을 직접 쓰지 않고, 파일 뷰 API 경로로 썸네일을 구성
                post.put("thumbUrl", "/api/files/" + fileId + "/view"); // 이미지 뷰어용 URL
            } else { // 이미지가 아니면 일반 파일로 처리
                post.put("fileType", "FILE"); // 일반 파일 타입
                post.put("thumbUrl", null);   // 썸네일 없음
            }
        }
        // --------------------------------------------------------------------

        // 3. DAO를 통해 전체 게시글 수 조회 (검색 조건을 반영한 count)
        int totalItems = boardDAO.countAll(searchType, searchKeyword); // totalItems: 조건에 맞는 전체 글 수

        // 4. 전체 페이지 수 계산 (나누기 후 올림 처리)
        int totalPages = (int) Math.ceil((double) totalItems / size); // 예: 101개, size=10 → 11페이지

        // 5. 결과를 담을 Map 생성
        Map<String, Object> result = new HashMap<>();    // 결과를 담을 HashMap 생성
        result.put("posts", posts);                      // "posts": 현재 페이지 게시글 목록
        result.put("totalItems", totalItems);            // "totalItems": 전체 게시글 수
        result.put("totalPages", totalPages);            // "totalPages": 전체 페이지 수
        result.put("currentPage", page);                 // "currentPage": 현재 페이지 번호

        // 6. 완성된 결과 Map 반환
        return result; // 컨트롤러로 전달되어 JSON 응답으로 사용됨
    } // getAllPosts 메소드 끝

    /**
     * 게시글 생성 + 첨부파일 저장
     * @param post   제목/내용 등이 들어 있는 Map
     * @param files  업로드된 첨부파일 목록
     * @param userId 작성자 ID
     * @return 생성된 게시글 전체 정보(Map). 실패 시 null.
     */
    @Override // IBoardService 인터페이스 메소드 구현
    public Map<String, Object> createPost(Map<String, Object> post, List<MultipartFile> files, String userId) {
        // 1. 작성자 ID 설정 (누가 쓴 글인지 DB에 기록)
        post.put("user_id", userId); // "user_id" 컬럼에 현재 사용자 ID 세팅

        // 2. DB에 게시글 INSERT (boardDAO.save에서 post_id 생성/주입)
        int affectedRows = boardDAO.save(post); // 1 이상이면 성공, 0 이하면 실패

        if (affectedRows <= 0) { // INSERT 실패 시
            return null; // null 반환하여 실패를 알림
        }

        // 3. 방금 INSERT된 게시글 ID 가져오기 (MyBatis에서 useGeneratedKeys 등으로 post_id 세팅했다고 가정)
        Object postIdObj = post.get("post_id"); // save 이후 post Map에 채워진 PK
        if (!(postIdObj instanceof Number)) {   // Number 타입이 아니면
            return null; // 안전 장치: 비정상 상황이므로 null 반환
        }
        int postId = ((Number) postIdObj).intValue(); // Number → int 변환

        // 4. 첨부파일이 있다면 저장
        if (files != null && !files.isEmpty()) {           // 업로드된 파일 목록이 비어 있지 않으면
            fileService.saveFilesForPost(postId, files);   // 게시글 ID와 함께 파일 저장 + post_files INSERT
        }

        // 5. 최종적으로 생성된 게시글 전체 정보(첨부파일 포함)를 다시 조회해서 반환
        return getPost(postId); // 단순 post 대신 getPost(postId)로 최신 상태 조회 후 반환
    }

    /**
     * 특정 게시글 단건 조회
     * @param postId 조회할 게시글 ID
     * @return 게시글 정보(Map) 또는 null
     */
    @Override // IBoardService 인터페이스 메소드 구현
    public Map<String, Object> getPost(int postId) { // 게시글 단건 조회 메소드
        // boardDAO.findById()는 Optional<Map>을 반환하므로, 값이 없으면 null 반환
        return boardDAO.findById(postId).orElse(null); // 존재하면 Map, 없으면 null
    } // getPost 메소드 끝

    /**
     * 게시글 수정 + 첨부파일 추가/삭제
     * @param postId        수정할 게시글 ID
     * @param postDetails   수정할 제목/내용 등이 들어있는 Map
     * @param newFiles      새로 추가할 파일 목록
     * @param deleteFileIds 삭제할 파일 ID 목록
     * @param currentUserId 현재 로그인 사용자 ID
     * @return 수정된 게시글 정보(Map). 권한 없음/실패 시 null.
     */
    @Override // IBoardService 인터페이스 메소드 구현
    public Map<String, Object> updatePost(
            int postId,                        // 수정 대상 게시글 ID
            Map<String, Object> postDetails,   // 수정할 제목/내용 정보
            List<MultipartFile> newFiles,      // 새로 추가할 첨부파일 목록
            List<Integer> deleteFileIds,       // 삭제할 첨부파일 ID 목록
            String currentUserId) {            // 현재 로그인 사용자 ID

        // 1. 수정 대상 게시글 조회
        Map<String, Object> post = boardDAO.findById(postId).orElse(null); // Optional → Map 또는 null

        // 2. 권한 확인 (게시글 존재 + 작성자 일치 여부)
        if (post == null || !post.get("user_id").equals(currentUserId)) { // 글이 없거나, 현재 유저가 작성자가 아니면
            return null; // 권한 없음 또는 게시글 없음 → 수정 불가
        }

        // editor 인라인 이미지 정리를 위한 이전/이후 내용 확보
        String oldContent = (String) post.get("content");           // 수정 전 content
        String newContent = (String) postDetails.get("content");    // 수정 후 content

        // 3. 제목/내용 수정
        post.put("title", postDetails.get("title")); // 제목 갱신
        post.put("content", newContent);             // 본문 갱신 (변수 newContent 사용)

        // 4. DB에 게시글 UPDATE
        int affectedRows = boardDAO.update(post); // update 결과(영향받은 행 수)

        if (affectedRows <= 0) { // UPDATE 실패 시
            return null; // null 반환
        }

        // UPDATE 성공 후 editor 인라인 이미지 정리 (내용 변경에 따른 이미지 정리)
        cleanupEditorImagesOnUpdate(oldContent, newContent); // 사용 안 하는 에디터 이미지를 정리

        // 5. 첨부파일 삭제 처리 (체크된 파일들)
        if (deleteFileIds != null && !deleteFileIds.isEmpty()) { // 삭제할 파일 ID 목록이 있다면
            fileService.deleteFilesByIds(deleteFileIds);         // DB/디스크에서 선택된 파일 삭제
        }

        // 6. 새로 추가된 첨부파일 저장
        if (newFiles != null && !newFiles.isEmpty()) {        // 새 파일 목록이 비어 있지 않으면
            fileService.saveFilesForPost(postId, newFiles);   // 게시글에 새 첨부파일 추가
        }

        // 7. 수정 후 최신 게시글 정보(첨부파일 포함)를 다시 조회해서 반환
        return getPost(postId); // post 대신 DB에서 다시 읽어온 최신 상태 반환
    }

    /**
     * 게시글 삭제 (관리자 또는 작성자만 가능)
     * @param postId        삭제할 게시글 ID
     * @param currentUserId 현재 사용자 ID
     * @param roles         현재 사용자 역할 목록 (예: ["USER"], ["ADMIN"])
     * @return 삭제 성공 여부
     */
    @Override // IBoardService 인터페이스 메소드 구현
    public boolean deletePost(int postId, String currentUserId, List<String> roles) {
        // 1. 삭제 대상 게시글 조회
        Map<String, Object> post = boardDAO.findById(postId).orElse(null); // Optional → Map 또는 null

        // 2. 게시글 존재 + (관리자이거나 작성자가 같은 경우)에만 삭제 허용
        if (post != null && (roles.contains("ADMIN") || post.get("user_id").equals(currentUserId))) {

            // 게시글 삭제 전에 본문에 연결된 editor 인라인 이미지 정리
            String content = (String) post.get("content"); // 삭제 전 게시글 본문
            cleanupEditorImagesOnDelete(content);          // 이 글에서만 사용하는 인라인 이미지 삭제

            // 게시글 삭제 전에 첨부 파일 전체 삭제
            //  1) post_files에서 메타데이터 조회
            //  2) 디스크에서 실제 파일 삭제
            //  3) post_files 메타데이터 삭제
            fileService.deleteFilesByPostId(postId);       // 첨부파일 + 빈 폴더까지 정리

            // 기존 로직: 게시글 자체 삭제 (board 테이블에서 DELETE)
            return boardDAO.delete(postId) > 0; // 1 이상이면 true, 아니면 false
        }

        // 게시글이 없거나 권한이 없는 경우 false 반환
        return false;
    }

    /**
     * 특정 게시글 조회수 1 증가
     * @param postId 조회수 증가시킬 게시글 ID
     */
    @Override // IBoardService 인터페이스 메소드 구현
    public void incrementViewCount(int postId) {
        boardDAO.incrementViewCount(postId); // DAO에게 조회수 증가 요청
    }

    /**
     * 게시글 고정 (관리자 전용)
     * @param postId        고정할 게시글 ID
     * @param order         고정 순서 (1, 2, 3 등)
     * @param currentUserId 현재 사용자 ID
     * @param roles         현재 사용자 역할 목록
     * @return 고정 성공 여부 (고정 3개 초과 시 false)
     */
    @Override // IBoardService 인터페이스 메소드 구현
    public boolean pinPost(int postId, int order, String currentUserId, List<String> roles) {
        // 1. 관리자 권한 확인 (roles 리스트에 "ADMIN"이 포함되어 있는지 체크)
        if (!roles.contains("ADMIN")) {
            // 관리자 권한이 아니면 고정 금지
            return false;
        }

        // 2. 현재 고정된 게시글 수 확인
        int currentPinnedCount = boardDAO.countPinned(); // pinned_order NOT NULL인 글 개수

        // 3. 고정 개수 3개 제한 확인
        if (currentPinnedCount >= 3) {
            // 제한 초과 로그 출력 (선택 사항)
            System.out.println("고정 개수 제한 초과: 최대 3개까지만 고정 가능.");
            // 4개 이상은 고정 불가
            return false;
        }

        // 4. 권한 있고 제한 개수 미만이면, 고정 작업 진행
        int affectedRows = boardDAO.updatePinnedOrder(postId, order); // pinned_order 업데이트

        // 5. 영향을 받은 행 수가 0보다 크면 성공으로 간주
        return affectedRows > 0;
    }

    /**
     * 게시글 고정 해제 (관리자 전용)
     * @param postId        고정 해제할 게시글 ID
     * @param currentUserId 현재 사용자 ID
     * @param roles         현재 사용자 역할 목록
     * @return 고정 해제 성공 여부
     */
    @Override // IBoardService 인터페이스 메소드 구현
    public boolean unpinPost(int postId, String currentUserId, List<String> roles) {
        // 1. 관리자 권한 확인
        if (!roles.contains("ADMIN")) { // ADMIN이 아니면
            return false;               // 고정 해제 불가
        }

        // 2. pinned_order를 null로 만들어 고정 해제
        int affectedRows = boardDAO.updatePinnedOrder(postId, null); // pinned_order = null

        // 3. 영향을 받은 행 수가 0보다 크면 성공
        return affectedRows > 0;
    }

    // ======================================================================
    // editor 인라인 이미지 정리를 위한 private 헬퍼 메소드들
    // ======================================================================

    // 본문 HTML에서 <img src="/api/editor-images/view/파일명"> 패턴만 뽑아서
    // "파일명(UUID_원본명)" 리스트로 반환하는 메소드
    private List<String> extractEditorImageFileNames(String html) {
        List<String> result = new ArrayList<>();             // 결과를 담을 리스트

        if (html == null || html.isBlank()) return result;   // 본문이 비어 있으면 빈 리스트 반환

        // <img ... src="/api/editor-images/view/파일명"...> 형태의 태그를 찾는 정규식
        Pattern p = Pattern.compile(
                "<img[^>]+src=[\"'](/api/editor-images/view/([^\"']+))[\"'][^>]*>", // 그룹2: 파일명 부분
                Pattern.CASE_INSENSITIVE                                          // IMG, Img 등 대소문자 무시
        );
        Matcher m = p.matcher(html); // HTML 전체에서 패턴 일치 여부를 검사하는 Matcher 생성

        // HTML 안에서 패턴과 일치하는 <img> 태그를 모두 찾음
        while (m.find()) {                     // find()가 true일 때마다 하나씩 매칭된 태그
            String fileName = m.group(2);      // 그룹2: /api/editor-images/view/ 뒤의 파일명만 추출
            result.add(fileName);              // 추출된 파일명을 결과 리스트에 추가
        }

        return result; // 추출된 파일명 리스트 반환
    }

    // 수정 시: oldHtml 에만 있고 newHtml 에는 없는 editor 이미지 파일들만 삭제
    private void cleanupEditorImagesOnUpdate(String oldHtml, String newHtml) {
        // 수정 전 본문에서 사용하던 이미지 파일명 목록
        List<String> before = extractEditorImageFileNames(oldHtml);
        // 수정 후 본문에서 사용하고 있는 이미지 파일명 목록
        List<String> after  = extractEditorImageFileNames(newHtml);

        // before 목록을 기반으로 집합 생성
        Set<String> toDelete = new HashSet<>(before); // 수정 전 사용 이미지 전체
        toDelete.removeAll(after);                    // 수정 후에도 남아 있는 파일명은 제거 → 차집합

        // toDelete: "수정 후 본문에는 더 이상 존재하지 않는" 이미지 파일명 목록
        for (String fn : toDelete) {
            // 이 파일을 실제로 삭제해도 되는지 DB에서 사용 여부를 확인한 뒤 삭제 시도
            deleteEditorImageFileOnUpdate(fn);
        }
    }

    // 삭제 시: 해당 글 본문에 포함된 editor 이미지 전부 삭제 (다른 글에서 안 쓸 때만)
    private void cleanupEditorImagesOnDelete(String html) { // 게시글 삭제 시 호출
        // 이 글의 본문에서 사용하던 에디터 이미지 파일명 목록
        List<String> files = extractEditorImageFileNames(html);
        for (String fn : files) { // 각 파일명에 대해
            // 실제 삭제해도 되는지 DB 사용 개수를 확인한 뒤 삭제
            deleteEditorImageFileOnDelete(fn);
        }
    }

    // 수정(update) 시에 쓰는 삭제 로직:
    //  - 이미 이 글에서는 사용이 제거된 상태
    //  - 다른 글에서 계속 사용 중이면 DB count 가 1 이상이므로 파일 삭제하지 않음
    private void deleteEditorImageFileOnUpdate(String savedName) {
        try {
            if (savedName == null || savedName.isBlank()) return; // 파일명이 비어 있으면 처리할 필요 없음

            // 현재 DB에서 이 파일명을 사용하는 게시글 수 조회
            int count = boardDAO.countPostsUsingEditorImage(savedName);

            // 수정 후에도 다른 게시글에서 사용 중이면 파일 삭제 금지
            if (count > 0) { // 1개 이상이면, 최소 한 글 이상에서 사용 중
                return;      // 실제 파일 삭제하지 않고 그대로 둠
            }

            // editor 이미지는 uploadDir/editor 하위에 저장된다는 가정
            Path editorBasePath = Paths.get(uploadDir, "editor"); // "C:/upload/editor" 경로
            Path target = editorBasePath.resolve(savedName);      // "C:/upload/editor/파일명"

            Files.deleteIfExists(target); // 파일이 존재하면 삭제, 없으면 조용히 무시
        } catch (Exception e) {            // 어떤 예외가 발생해도
            e.printStackTrace();           // 로그만 찍고 서비스 전체가 죽지 않도록 함
        }
    }

    // 삭제(delete) 시에 쓰는 삭제 로직:
    //  - 아직 이 글의 content도 DB에 남아있기 때문에
    //  - count == 1  → 이 글만 사용 → 삭제 OK
    //  - count >= 2 → 다른 글도 사용 → 삭제 금지
    private void deleteEditorImageFileOnDelete(String savedName) {
        try {
            // 삭제하려는 이미지 파일 이름이 null이거나 빈 문자열이면 삭제 작업 불필요
            if (savedName == null || savedName.isBlank()) return;

            // 현재 DB에서 이 파일명을 사용하는 게시글 수 조회
            int count = boardDAO.countPostsUsingEditorImage(savedName);

            // 이 글 말고 다른 게시글에서도 쓰이면 삭제하지 않음
            if (count > 1) { // 2개 이상이면 현재 글 외에도 다른 글이 해당 이미지를 사용
                return;      // 실제 파일을 삭제하지 않고 유지
            }

            // editor 이미지는 uploadDir/editor 하위에 저장됨
            Path editorBasePath = Paths.get(uploadDir, "editor");  // "C:/upload/editor"
            Path target = editorBasePath.resolve(savedName);       // "C:/upload/editor/파일명"

            Files.deleteIfExists(target); // 파일이 존재할 경우에만 삭제 시도
        } catch (Exception e) {
            // 에디터 이미지 삭제 실패해도 게시글 삭제 자체는 진행되도록 예외만 로그 출력
            e.printStackTrace();
        }
    }
    // ======================================================================

} // BoardServiceImpl 클래스 정의 끝
