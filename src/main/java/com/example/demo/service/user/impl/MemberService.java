package com.example.demo.service.user.impl;

import com.example.demo.dao.LoginAccountDAO; 
import com.example.demo.dao.UserDAO;
import com.example.demo.entity.LoginAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class MemberService {

    @Autowired
    private UserDAO userDAO; //uesrs 테이블에 접근하는 DAO

    @Autowired
    private LoginAccountDAO loginAccountDAO; // login_accounts 테이블에 접근하기 위한 loginAccountDAO

    @Autowired
    private PasswordEncoder passwordEncoder; //security 설정에서 빈으로 등록한 PasswordEncoder(암호화 도구)

    @Transactional
    public void register(Map<String, String> userParams) {
        // 1. 프로필 정보 저장 (이 부분은 동일합니다)
        Map<String, Object> userProfile = new HashMap<>(); //uesrDAO.save() 메서드에 전달할 Map 객체 생성
        userProfile.put("name", userParams.get("name")); // userparams에서 name키에 해당하는 값을 꺼내서 userProfile 맵에 name키로 다시 넣음
        userProfile.put("phone", userParams.get("phone")); // userparams에서 phone키에 해당하는 값을 꺼내서 userProfile 맵에 phone키로 다시 넣음
        userProfile.put("email", userParams.get("email")); // userparams에서 email키에 해당하는 값을 꺼내서 userProfile 맵에 email키로 다시 넣음
        userDAO.save(userProfile);

        Integer userId = (Integer) userProfile.get("id"); // userProfile 맵에서 id키에 해당하는 값을 꺼내서 userId 변수에 저장

        // 2. 인증 정보 저장 (이 부분은 동일합니다)
        LoginAccount account = new LoginAccount(); // LoginAccount 객체 생성
        account.setUserId(userId.longValue()); // userId를 Long 타입으로 변환하여 설정
        account.setEmail(userParams.get("email")); // 이메일 설정 
        account.setPassword(passwordEncoder.encode(userParams.get("password"))); // 비밀번호를 암호화하여 설정
        account.setRole("USER"); // 기본 역할을 USER로 설정

        loginAccountDAO.save(account); // loginAccountDAO를 사용하여 계정 정보 저장
    }
}