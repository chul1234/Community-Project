package com.example.demo.secutiry;

import com.example.demo.dao.LoginAccountDAO; 
import com.example.demo.entity.LoginAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService { // UserDetailsService 인터페이스 구현

    @Autowired
    private LoginAccountDAO loginAccountDAO;

    @Override
    // 로그인 버튼 누를시 자동으로 호출, 아이디(이메일) 전달 받음,메소드 이름은 loadUserByUsername통해 이메일을 사용해 사용자 찾음 
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException { 
        LoginAccount account = loginAccountDAO.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("계정을 찾을 수 없습니다: " + email));
                //UsernameNotFoundException: Spring Security에게 "요청한 아이디를 가진 사용자가 없습니다"라고 알려주는 표준 예외

        return User.builder()
                .username(account.getEmail())
                .password(account.getPassword())
                .roles(account.getRole())
                .build();
    }
}