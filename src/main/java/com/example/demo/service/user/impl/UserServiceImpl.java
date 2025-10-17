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
        return userDAO.findByUserId(userId).orElse(null);
    }

    @Override
    @Transactional
    public Map<String, Object> createUser(Map<String, Object> user) {
        String rawPassword = (String) user.get("password");
        user.put("password", passwordEncoder.encode(rawPassword));

        int affectedRows = userDAO.save(user);

        if (affectedRows > 0) {
            String newUserId = (String) user.get("user_id");
            
            if (newUserId != null) {
                userDAO.insertUserRole(newUserId, "USER"); 
            }
            return user;
        } else {
            return null;
        }
    }

    @Override
    public Map<String, Object> updateUser(String userId, Map<String, Object> userDetails) {
        Map<String, Object> user = userDAO.findByUserId(userId).orElse(null);
        if (user != null) {
            user.put("name", userDetails.get("name"));
            user.put("phone", userDetails.get("phone"));
            user.put("email", userDetails.get("email"));
            
            user.put("id_for_update", userId);
            int affectedRows = userDAO.save(user);

            if (affectedRows == 1) {
                return user;
            }
        }
        return null;
    }

    /**
     * [수정] 사용자를 삭제하기 전에, 연관된 역할 정보(users_roles)부터 먼저 삭제하도록 변경
     * @param userId 삭제할 사용자의 ID
     */
    @Override
    @Transactional // 두 개의 삭제 작업을 하나의 트랜잭션으로 묶어 데이터 정합성을 보장합니다.
    public void deleteUser(String userId) {
        try {
            // 1. 먼저 users_roles 테이블에서 해당 사용자의 모든 역할 정보를 삭제합니다.
            userDAO.deleteUserRoles(userId);

            // 2. 역할 정보가 성공적으로 삭제되면, users 테이블에서 사용자 정보를 삭제합니다.
            int affectedRows = userDAO.deleteByUserId(userId);
            
            // 만약 users 테이블에서 삭제된 행이 없다면, 예외를 발생시켜 롤백합니다.
            if (affectedRows == 0) {
                throw new RuntimeException("삭제할 사용자를 찾지 못했습니다. ID: " + userId);
            }
        } catch (Exception e) {
            // @Transactional에 의해 중간에 오류가 발생하면 모든 작업이 자동으로 롤백(취소)됩니다.
            throw new RuntimeException("사용자 삭제 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public List<Map<String, Object>> findAllUsers() {
        return userDAO.findAll();
    }

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
                                userDAO.insertUserRole(newUserId, "USER");
                            }
                            return 1;
                        } else {
                            return 0;
                        }
                    })
                    .collect(Collectors.toList());
    }
    
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