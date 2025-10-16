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
     * [수정됨] 단일 사용자 생성 시 users_roles 테이블에 기본 역할을 추가하는 로직으로 변경
     */
    @Override
    @Transactional // users 테이블과 users_roles 테이블 작업을 하나의 트랜잭션으로 묶습니다.
    public Map<String, Object> createUser(Map<String, Object> user) {
        // 1. 비밀번호를 암호화합니다.
        String rawPassword = (String) user.get("password");
        user.put("password", passwordEncoder.encode(rawPassword));

        // 2. users 테이블에 사용자 정보를 저장합니다. (role_id는 이제 없음)
        int affectedRows = userDAO.save(user);

        if (affectedRows > 0) {
            // 3. 저장이 성공하면, 생성된 사용자의 id를 가져옵니다.
            Integer newUserId = (Integer) user.get("id");
            
            // 4. users_roles 테이블에 기본 역할(USER, role_id=2)을 부여합니다.
            if (newUserId != null) {
                userDAO.insertUserRole(newUserId, 2); // 기본 'USER' 역할 ID
            }
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
     * [수정됨] 여러 사용자 등록 시 users_roles 테이블에 기본 역할을 추가하는 로직으로 변경
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
                        
                        // 3. 암호화된 정보로 users 테이블에 저장합니다.
                        int affectedRows = userDAO.save(user);

                        if (affectedRows > 0) {
                            // 4. 저장이 성공하면, 생성된 사용자의 id를 가져와 users_roles에 기본 역할을 부여합니다.
                            Integer newUserId = (Integer) user.get("id");
                            if (newUserId != null) {
                                userDAO.insertUserRole(newUserId, 2); // 기본 'USER' 역할 ID
                            }
                            return 1; // 성공
                        } else {
                            return 0; // 실패
                        }
                    })
                    .collect(Collectors.toList());
    }

    /**
     * [교체됨] 기존 updateUserRole 메소드를 삭제하고 이 메소드로 대체합니다.
     * 특정 사용자의 역할을 변경합니다.
     * @param userId 변경할 사용자의 ID
     * @param roleIds 새로 부여할 역할 ID 목록
     */
    @Override
    @Transactional // 이 어노테이션 덕분에 내부의 모든 DB 작업이 하나의 단위로 묶여 안전하게 실행됩니다.
    public void updateUserRoles(Integer userId, List<Integer> roleIds) {
        try {
            // 1. UserDAO를 통해 해당 사용자의 기존 역할을 모두 삭제합니다.
            userDAO.deleteUserRoles(userId);
            
            // 2. 프론트엔드에서 새로운 역할 목록을 전달받았다면, 모두 새로 추가합니다.
            if (roleIds != null && !roleIds.isEmpty()) {
                userDAO.insertUserRoles(userId, roleIds);
            }
        } catch (Exception e) {
            // @Transactional에 의해 중간에 오류가 나면 모든 작업이 자동으로 롤백(취소)됩니다.
            throw new RuntimeException("사용자 역할 변경 중 오류가 발생했습니다.", e);
        }
    }
}