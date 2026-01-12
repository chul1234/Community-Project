package com.example.demo.service.bmk;

import java.util.List;
import java.util.Map;

public interface IBmkService {

    /**
     * 즐겨찾기 토글 (On/Off)
     * @param userId 사용자ID
     * @param targetType 대상 종류(BUS/STOP)
     * @param targetId 대상 ID
     * @param alias 별칭 (이름) - DB 저장을 위한 스냅샷
     * @return true if added (On), false if removed (Off)
     */
    boolean toggleBookmark(String userId, String targetType, String targetId, String alias);

    /**
     * 즐겨찾기 여부 확인
     */
    boolean checkBookmark(String userId, String targetType, String targetId);

    /**
     * 내 즐겨찾기 목록 조회
     */
    List<Map<String, Object>> getMyBookmarks(String userId);
}
