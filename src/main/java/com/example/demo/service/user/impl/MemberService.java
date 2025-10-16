package com.example.demo.service.user.impl;

import com.example.demo.dao.UserDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class MemberService {

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional // 두 개의 DB 작업을 하나의 단위로 묶어 안전하게 처리합니다.
    public void register(Map<String, String> userParams) {
        // 1. 사용자 정보를 하나의 Map에 모두 담습니다.
        Map<String, Object> user = new HashMap<>();
        user.put("name", userParams.get("name"));
        user.put("phone", userParams.get("phone"));
        user.put("email", userParams.get("email"));
        user.put("user_id", userParams.get("user_id")); 
        user.put("password", passwordEncoder.encode(userParams.get("password")));
        
        // 2. 먼저 users 테이블에 사용자 정보를 저장합니다.
        int affectedRows = userDAO.save(user);

        // 3. users 테이블에 저장이 성공했다면,
        if (affectedRows > 0) {
            // 4. [수정] 방금 생성된 사용자의 user_id(PK, String)를 가져옵니다.
            String newUserId = (String) user.get("user_id");

            // 5. [수정] 그 user_id를 이용해 users_roles 테이블에 기본 'USER' 역할을 '문자열'로 추가합니다.
            if (newUserId != null && !newUserId.isEmpty()) {
                userDAO.insertUserRole(newUserId, "USER");
            }
        } else {
            // users 테이블 저장 실패 시 예외를 발생시켜 롤백합니다.
            throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다.");
        }
    }
}