package com.example.demo.service.bigpost;

import java.util.List;
import java.util.Map;

public interface IBigPostService {

    // 기존 OFFSET 페이징
    Map<String, Object> getBigPosts(int page, int size);

    // ▼▼▼ 초고속 키셋 페이징용 ▼▼▼  // 수정됨
    List<Map<String, Object>> getFirstPage(int size);     // 수정됨
    List<Map<String, Object>> getNextPage(long lastId, int size);  // 수정됨
}
