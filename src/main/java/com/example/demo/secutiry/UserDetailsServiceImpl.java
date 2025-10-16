package com.example.demo.secutiry;

import com.example.demo.dao.UserDAO; // UserDAO를 import 합니다.
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Map; // Map을 사용하기 위해 import 합니다.

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserDAO userDAO; // LoginAccountDAO 대신 UserDAO를 주입받습니다.

    @Override
    // Spring Security는 login.html의 name="username" 필드 값을 이 메소드의 파라미터(username)로 전달합니다.
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        // userDAO의 findByUserId 메소드를 호출하여 로그인 ID로 사용자를 찾습니다.
        Map<String, Object> user = userDAO.findByUserId(username)
                .orElseThrow(() -> new UsernameNotFoundException("계정을 찾을 수 없습니다: " + username));

        // DB에서 조회한 정보로 Spring Security가 이해할 수 있는 UserDetails 객체를 생성합니다.
        String roleName = (String) user.get("role_name"); // "USER", "ADMIN" 등 역할 이름

        return User.builder()
                .username((String) user.get("user_id"))
                .password((String) user.get("password"))
                .roles(roleName)
                .build();
    }
}