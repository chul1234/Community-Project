package com.example.demo.controller;

import com.example.demo.service.user.IUserService;
import java.util.List;
import java.util.Map; // Map import 추가

//spring 의존성 주입 기능
import org.springframework.beans.factory.annotation.Autowired;
//HTTP 응답을 나타내는 ResponseEntity import
import org.springframework.http.ResponseEntity;
//REST 컨트롤러임을 spring에게 알림
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;



@RestController //@RestController: 이 클래스가 RESTful API의 컨트롤러,반환값을 자동으로 JSON 형태로 변환
public class UserController {
    @Autowired
    private IUserService userService;
    // HTTP GET 요청이 "/users" 경로로 들어오면 이 메소드가 호출
    @GetMapping("/users")
    public List<Map<String, Object>> getAllUsers() {
        return userService.findAllUsers();
    }
    
    // HTTP GET 요청이 "/users/{id}" 경로로 들어오면 이 메소드가 호출
    @GetMapping("/users/{id}")
    // @PathVariable: URL 경로에서 {id} 값을 추출하여 메소드 매개변수 id에 할당
    public Map<String, Object> getUser(@PathVariable String id) {
        //userService의 getUser 메소드 호출, DB에서 사용자 조회
        return userService.getUser(id);
    }

    // HTTP POST 요청이 "/users" 경로로 들어오면 이 메소드가 호출
    @PostMapping("/users")
    // @RequestBody: HTTP 요청 본문에 담긴 JSON 데이터를 Map<String, Object> 타입의 user 매개변수로 변환
    public Map<String, Object> createUser(@RequestBody Map<String, Object> user) {
        //userService의 createUser 메소드 호출, DB에 사용자 생성
        return userService.createUser(user);
    }

    // HTTP PUT 요청이 "/users/{id}" 경로로 들어오면 이 메소드가 호출
    @PutMapping("/users/{id}")
    // @PathVariable: URL 경로에서 {id} 값을 추출하여 메소드 매개변수 id에 할당
    public Map<String, Object> updateUser(@PathVariable String id, @RequestBody Map<String, Object> user) {
        //userService의 updateUser 메소드 호출, DB에서 사용자 정보 업데이트
        return userService.updateUser(id, user);
    }

    // HTTP DELETE 요청이 "/users/{id}" 경로로 들어오면 이 메소드가 호출
    @DeleteMapping("/users/{id}")
    // @PathVariable: URL 경로에서 {id} 값을 추출하여 메소드 매개변수 id에 할당
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        //userService의 deleteUser 메소드 호출, DB에서 사용자 삭제
        userService.deleteUser(id);
        //삭제 후 응답 본문 없이 상태 코드 204(No Content) 반환
        return ResponseEntity.noContent().build();
    }

    // HTTP POST 요청이 "/users/bulk" 경로로 들어오면 이 메소드가 호출
    @PostMapping("/users/bulk")
    // @RequestBody: HTTP 요청 본문에 담긴 JSON 데이터를 List<Map<String, Object>> 타입의 users 매개변수로 변환
    public List<Integer> createUsers(@RequestBody List<Map<String, Object>> users) {
        //userService의 createUsers 메소드 호출, DB에 여러 사용자 생성
        return userService.createUsers(users);
    }

    @GetMapping("/api/me")
    public ResponseEntity<Map<String, String>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        String role = authentication.getAuthorities().stream()
                .findFirst() // 첫 번째 권한을 가져옵니다.
                .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", "")) // "ROLE_" 접두사 제거
                .orElse("NONE"); // 권한이 없으면 "NONE"

        // Map에 username과 role을 함께 담아 반환합니다.
        Map<String, String> response = new java.util.HashMap<>();
        response.put("username", username);
        response.put("role", role);

        return ResponseEntity.ok(response);
    }

}