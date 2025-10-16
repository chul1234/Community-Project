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
    private UserDAO userDAO; // 이제 UserDAO만 사용합니다.

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public void register(Map<String, String> userParams) {
        // 1. 사용자 정보를 하나의 Map에 모두 담습니다.
        Map<String, Object> user = new HashMap<>();
        user.put("name", userParams.get("name"));
        user.put("phone", userParams.get("phone"));
        user.put("email", userParams.get("email"));
        
        // 로그인 ID로 사용할 'user_id'를 추가합니다.
        // (주의: register.html 폼에 user_id 입력 필드가 추가되어야 합니다.)
        user.put("user_id", userParams.get("user_id")); 

        // 비밀번호는 반드시 암호화해서 저장합니다.
        user.put("password", passwordEncoder.encode(userParams.get("password")));
        
        // roles 테이블의 'USER'에 해당하는 role_id가 2라고 가정합니다.
        // (만약 다르다면 이 숫자를 수정해야 합니다.)
        user.put("role_id", 2);

        // 2. UserDAO를 한 번만 호출하여 모든 정보를 저장합니다.
        userDAO.save(user);
    }
}