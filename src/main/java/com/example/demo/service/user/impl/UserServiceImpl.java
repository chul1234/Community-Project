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

    // 비밀번호 암호화를 위해 PasswordEncoder를 주입받습니다.
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> getUser(String id) {
        return userDAO.findById(Integer.parseInt(id)).orElse(null);
    }

    /**
     * [수정됨] 단일 사용자 생성 시 비밀번호 암호화 및 기본 역할 부여
     */
    @Override
    public Map<String, Object> createUser(Map<String, Object> user) {
        // 1. 비밀번호를 암호화합니다.
        String rawPassword = (String) user.get("password");
        user.put("password", passwordEncoder.encode(rawPassword));

        // 2. 기본 역할(USER, role_id=2)을 부여합니다.
        user.put("role_id", 2);

        int affectedRows = userDAO.save(user);

        if (affectedRows > 0) {
            return user;
        } else {
            return null;
        }
    }

    @Override
    public Map<String, Object> updateUser(String id, Map<String, Object> userDetails) {
        Map<String, Object> user = userDAO.findById(Integer.parseInt(id)).orElse(null);
        if (user != null) {
            user.put("name", userDetails.get("name"));
            user.put("phone", userDetails.get("phone"));
            user.put("email", userDetails.get("email"));
            
            int affectedRows = userDAO.save(user);

            if (affectedRows == 1) {
                return user;
            }
        }
        return null;
    }

    @Override
    public void deleteUser(String id) {
        int affectedRows = userDAO.deleteById(Integer.parseInt(id));
        if (affectedRows == 0) {
            throw new RuntimeException("삭제할 사용자를 찾지 못했습니다. ID: " + id);
        }
    }

    @Override
    public List<Map<String, Object>> findAllUsers() {
        return userDAO.findAll();
    }

    /**
     * [수정됨] 여러 사용자 등록 시 비밀번호를 암호화하고, 기본 역할을 부여합니다.
     */
    @Override
    @Transactional
    public List<Integer> createUsers(List<Map<String, Object>> users) {
        return users.stream()
                    .map(user -> {
                        // 1. 사용자가 입력한 비밀번호(평문)를 가져옵니다.
                        String rawPassword = (String) user.get("password");
                        
                        // 2. 비밀번호를 암호화하여 다시 user Map에 저장합니다.
                        user.put("password", passwordEncoder.encode(rawPassword));

                        // 3. 기본 역할(USER, role_id=2)을 부여합니다.
                        user.put("role_id", 2);

                        // 4. 암호화된 정보로 DB에 저장하고 결과를 반환합니다.
                        int affectedRows = userDAO.save(user);
                        return affectedRows > 0 ? 1 : 0;
                    })
                    .collect(Collectors.toList());
    }
    /**
     * 특정 사용자의 역할을 변경합니다.
     * @param userId 변경할 사용자의 ID
     * @param roleId 새로 부여할 역할 ID
     */
    @Override
    public void updateUserRole(Integer userId, Integer roleId) {
        int affectedRows = userDAO.updateRole(userId, roleId);

        // 만약 업데이트가 실패했다면(영향받은 행이 0개라면) 예외를 발생시킵니다.
        if (affectedRows == 0) {
            throw new RuntimeException("역할을 변경할 사용자를 찾지 못했거나 변경에 실패했습니다. User ID: " + userId);
        }
    }
}