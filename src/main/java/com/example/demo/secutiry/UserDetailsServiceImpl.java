package com.example.demo.secutiry;

import com.example.demo.dao.UserDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserDAO userDAO;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        Map<String, Object> user = userDAO.findByUserId(username)
                .orElseThrow(() -> new UsernameNotFoundException("계정을 찾을 수 없습니다: " + username));

        // ▼▼▼▼▼ [최종 수정] user.get("role_name") 대신 user.get("role_ids")를 사용합니다. ▼▼▼▼▼
        String concatenatedRoles = (String) user.get("role_ids");
        
        String[] roles = new String[0]; 
        if (concatenatedRoles != null && !concatenatedRoles.isEmpty()) {
            roles = concatenatedRoles.split(", ");
        }

        return User.builder()
                .username((String) user.get("user_id"))
                .password((String) user.get("password"))
                .roles(roles)
                .build();
    }
}