package com.example.demo.service.user.impl;

import com.example.demo.dao.UserDAO;
import com.example.demo.service.user.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements IUserService {

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> getUser(String userId) {
        // [수정] findById 대신 findByUserId를 사용합니다.
        return userDAO.findByUserId(userId).orElse(null);
    }

    /**
     * [수정] 단일 사용자 생성 시 users_roles 테이블에 기본 역할을 '문자열'로 추가합니다.
     */
    @Override
    @Transactional
    public Map<String, Object> createUser(Map<String, Object> user) {
        String rawPassword = (String) user.get("password");
        user.put("password", passwordEncoder.encode(rawPassword));

        int affectedRows = userDAO.save(user);

        if (affectedRows > 0) {
            // [수정] 생성된 사용자의 id(PK)는 이제 user_id(String)입니다.
            String newUserId = (String) user.get("user_id");
            
            if (newUserId != null) {
                // [수정] 기본 역할 'USER'를 문자열로 부여합니다.
                userDAO.insertUserRole(newUserId, "USER"); 
            }
            return user;
        } else {
            return null;
        }
    }

    @Override
    public Map<String, Object> updateUser(String userId, Map<String, Object> userDetails) {
        // [수정] user_id를 기준으로 사용자를 찾습니다.
        Map<String, Object> user = userDAO.findByUserId(userId).orElse(null);
        if (user != null) {
            user.put("name", userDetails.get("name"));
            user.put("phone", userDetails.get("phone"));
            user.put("email", userDetails.get("email"));
            
            // [수정] 업데이트 시 WHERE 조건절에 들어갈 키를 명확히 합니다.
            user.put("id_for_update", userId);
            int affectedRows = userDAO.save(user);

            if (affectedRows == 1) {
                return user;
            }
        }
        return null;
    }

    @Override
    public void deleteUser(String userId) {
        // [수정] deleteById 대신 deleteByUserId를 사용합니다.
        int affectedRows = userDAO.deleteByUserId(userId);
        if (affectedRows == 0) {
            throw new RuntimeException("삭제할 사용자를 찾지 못했습니다. ID: " + userId);
        }
    }

    @Override
    public List<Map<String, Object>> findAllUsers() {
        return userDAO.findAll();
    }

    /**
     * [수정] 여러 사용자 등록 시 users_roles 테이블에 기본 역할을 '문자열'로 추가합니다.
     */
    @Override
    @Transactional
    public List<Integer> createUsers(List<Map<String, Object>> users) {
        return users.stream()
                    .map(user -> {
                        String rawPassword = (String) user.get("password");
                        user.put("password", passwordEncoder.encode(rawPassword));

                        int affectedRows = userDAO.save(user);

                        if (affectedRows > 0) {
                            String newUserId = (String) user.get("user_id");
                            if (newUserId != null) {
                                // [수정] 기본 역할 'USER'를 문자열로 부여합니다.
                                userDAO.insertUserRole(newUserId, "USER");
                            }
                            return 1;
                        } else {
                            return 0;
                        }
                    })
                    .collect(Collectors.toList());
    }
    
    /**
     * [수정] 특정 사용자의 역할을 변경하는 메소드 (모두 String 타입 사용)
     */
    @Override
    @Transactional
    public void updateUserRoles(String userId, List<String> roleIds) {
        try {
            userDAO.deleteUserRoles(userId);
            
            if (roleIds != null && !roleIds.isEmpty()) {
                userDAO.insertUserRoles(userId, roleIds);
            }
        } catch (Exception e) {
            throw new RuntimeException("사용자 역할 변경 중 오류가 발생했습니다.", e);
        }
    }
}