package com.example.demo.service.user.impl;

import com.example.demo.dao.UserDAO;
import com.example.demo.service.user.IUserService;
//spring 의존성 주입 기능
import org.springframework.beans.factory.annotation.Autowired;
//service임을 spring에게 알림
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map; // Map import 추가
import java.util.stream.Collectors;

@Service //Spring이 비즈니스 로직을 처리하는 서비스 bean으로 관리
public class UserServiceImpl implements IUserService { // 클래스 선언, IUserService 인터페이스의 규칙을 따름
    @Autowired //의존성 주입
    private UserDAO userDAO; 

    @Override 
    public Map<String, Object> getUser(String id) { 
        //문자열 id를 정수(Integer)로 변환
        //userDAO의 findById 메소드 호출, DB에서 사용자 찾음
        //Optional에서 값이 있으면 반환, 없으면 null 반환
        return userDAO.findById(Integer.parseInt(id)).orElse(null);
    }

    /**
     * [수정됨] DAO의 save()가 반환하는 int(생성된 ID)를 확인하여 성공/실패를 판단합니다.
     */
    @Override
    public Map<String, Object> createUser(Map<String, Object> user) {
        // userDAO의 save 메소드는 성공 시 생성된 ID(>0)를 반환합니다.
        int affectedRows = userDAO.save(user);

        // 1건이 처리되었다면 성공으로 판단합니다.
        if (affectedRows > 0) {
            return user; // ID가 포함된 user 맵을 컨트롤러로 반환
        } else {
            return null; // 생성 실패 시 null 반환
        }
    }

    /**
     * [수정됨] DAO의 save()가 반환하는 int(영향받은 행 수)를 확인하여 성공/실패를 판단합니다.
     */
    @Override
    public Map<String, Object> updateUser(String id, Map<String, Object> userDetails) {
        //수정할 사용자가 존재하는지 확인(ID 조회)
        Map<String, Object> user = userDAO.findById(Integer.parseInt(id)).orElse(null);
        //null이 아니라면 (사용자 존재)
        if (user != null) {
            user.put("name", userDetails.get("name"));
            user.put("phone", userDetails.get("phone"));
            user.put("email", userDetails.get("email"));
            
            // userDAO의 save 메소드는 성공 시 1, 대상이 없으면 0을 반환합니다.
            int affectedRows = userDAO.save(user);

            // 정확히 1개의 행이 수정되었을 경우에만 성공으로 판단합니다.
            if (affectedRows == 1) {
                return user; // 수정된 user 맵을 컨트롤러로 반환
            }
        }
        //사용자 존재 X 또는 업데이트 실패 시 null로 반환
        return null;
    }

    /**
     * [수정됨] DAO의 deleteById()가 반환하는 int(영향받은 행 수)를 확인하여 성공/실패를 판단합니다.
     */
    @Override
    public void deleteUser(String id) {
        // userDAO의 deleteById 메소드는 성공 시 1, 대상이 없으면 0을 반환합니다.
        int affectedRows = userDAO.deleteById(Integer.parseInt(id));

        // 영향받은 행이 0이라면, 삭제할 대상이 없었음을 의미하므로 예외를 발생시킵니다.
        if (affectedRows == 0) {
            throw new RuntimeException("삭제할 사용자를 찾지 못했습니다. ID: " + id);
        }
        // 성공 시에는 (void이므로) 아무것도 반환하지 않습니다.
    }

    @Override
    public List<Map<String, Object>> findAllUsers() {
        //userDAO의 findAll 메소드 호출, DB에서 모든 사용자 조회
        return userDAO.findAll();
    }

    /**
     * [수정됨] 각 사용자의 저장 성공 여부를 DAO의 반환값으로 판단합니다.
     */
    @Override
    public List<Integer> createUsers(List<Map<String, Object>> users) {
        //users 리스트의 각 사용자 정보를 userDAO의 save 메소드로 저장
        return users.stream()
        //map에 대해 저장 작업 수행, 결과를 새로운 값으로 변환
                    .map(user -> {
                        // userDAO.save(user)는 성공 시 1을 반환합니다.
                        int affectedRows = userDAO.save(user);
                        // 저장이 성공하면 1을, 실패하면 0을 반환합니다. 
                        return affectedRows > 0 ? 1 : 0;
                    })
                    //collect()는 stream의 최종 결과를 List로 만들어줍니다.
                    //[1, 1, 0, 1] 과 같이 각 사용자의 성공/실패 결과가 담긴 리스트가 반환됩니다.
                    .collect(Collectors.toList());
    }
}