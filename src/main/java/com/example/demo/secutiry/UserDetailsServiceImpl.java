package com.example.demo.secutiry;

import com.example.demo.dao.UserDAO; // DB의 users 테이블과 통신하는 UserDAO
import org.springframework.beans.factory.annotation.Autowired; // Spring의 의존성 주입 기능을 사용하기 위한 도구
import org.springframework.security.core.userdetails.User; // Spring Security가 제공하는 UserDetails의 표준 구현체
import org.springframework.security.core.userdetails.UserDetails; // 사용자의 핵심 정보(ID, PW, 권한)를 담는 설계도
import org.springframework.security.core.userdetails.UserDetailsService; // '사용자 정보를 찾아오는 기능'의 설계도
import org.springframework.security.core.userdetails.UsernameNotFoundException; // 사용자를 찾지 못했을 때 발생시키는 예외
import org.springframework.stereotype.Service; // 이 클래스가 서비스 부품임을 알리는 도구
import java.util.Map; // 데이터를 '이름표-값' 쌍으로 다루기 위한 도구

@Service
//// 'UserDetailsServiceImpl'이라는 클래스를 선언하고, Spring Security의 'UserDetailsService' 설계도를 구현(implements)
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserDAO userDAO;
    //@Override: 부모 설계도(UserDetailsService)에 정의된 메소드를 재정의
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        // userDAO의 findByUserId 메소드를 호출하여 DB에서 사용자를 찾고, 그 결과를 'user'라는 Map 변수에 저장
        Map<String, Object> user = userDAO.findByUserId(username)
            // .orElseThrow(...): 만약 findByUserId가 비어있는 Optional(사용자를 못 찾음)을 반환
                // 화살표 뒤의 코드를 실행하여 UsernameNotFoundException을 발생시키고 메소드를 즉시 종료
                .orElseThrow(() -> new UsernameNotFoundException("계정을 찾을 수 없습니다: " + username));

        // user Map에서 'role_ids'라는 이름표(key)로 저장된 값(예: "USER,ADMIN")을 문자열 
        String concatenatedRoles = (String) user.get("role_ids");
        // 사용자의 역할을 담을 비어있는 문자열 배열(roles)을 미리 만듬사용자가 아무 역할도 없을 경우를 대비한 안전장치
        String[] roles = new String[0]; 
        // 만약 DB에서 가져온 역할 문자열(concatenatedRoles)이 null이 아니고 비어있지도 않다면,
        if (concatenatedRoles != null && !concatenatedRoles.isEmpty()) { 
            // ", " (쉼표와 공백)를 기준으로 문자열을 잘라서, 각 역할을 배열의 요소생성(ex["ADMIN","USER"])
            roles = concatenatedRoles.split(", ");
        }

        return User.builder()
                .username((String) user.get("user_id"))
                .password((String) user.get("password"))
                .roles(roles)
                .build(); //Spring Security의 User 객체를 생성 열할 배열을 전달 -> "ROLE_"접두어가 붙어 "ROLE_USER","ROLE_ADMIN"등으로 처리 
    }
}