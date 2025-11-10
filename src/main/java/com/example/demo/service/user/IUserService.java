package com.example.demo.service.user;

import java.util.List;
import java.util.Map;

public interface IUserService {

    // [유지] 모든 메소드의 파라미터 타입을 String으로 변경합니다.
    Map<String, Object> getUser(String userId);

    Map<String, Object> createUser(Map<String, Object> user);

    Map<String, Object> updateUser(String userId, Map<String, Object> user);

    void deleteUser(String userId);

    // ▼▼▼ [수정] findAllUsers 메소드 시그니처 변경 (IBoardService.java 참고) ▼▼▼
    // Map<String, Object> findAllUsers(int page, int size); // (기존)
    Map<String, Object> findAllUsers(int page, int size, String searchType, String searchKeyword); // (수정)
    // ▲▲▲ [수정] ▲▲▲

    List<Integer> createUsers(List<Map<String, Object>> users);
    
    // [유지] 역할 변경 메소드도 모두 String 타입을 사용하도록 변경합니다.
    void updateUserRoles(String userId, List<String> roleIds);
}