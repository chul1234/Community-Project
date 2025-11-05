package com.example.demo.service.user;

import java.util.List;
import java.util.Map;

public interface IUserService {

    // [수정] 모든 메소드의 파라미터 타입을 String으로 변경합니다.
    Map<String, Object> getUser(String userId);

    Map<String, Object> createUser(Map<String, Object> user);

    Map<String, Object> updateUser(String userId, Map<String, Object> user);

    void deleteUser(String userId);

    Map<String, Object> findAllUsers(int page, int size);

    List<Integer> createUsers(List<Map<String, Object>> users);
    
    // [수정] 역할 변경 메소드도 모두 String 타입을 사용하도록 변경합니다.
    void updateUserRoles(String userId, List<String> roleIds);
}