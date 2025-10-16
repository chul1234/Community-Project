package com.example.demo.controller;

import com.example.demo.service.user.IUserService;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
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
    public Map<String, Object> getUser(@PathVariable String id) {
        return userService.getUser(id);
    }

    // HTTP POST 요청이 "/users" 경로로 들어오면 이 메소드가 호출
    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> user) {
        return userService.createUser(user);
    }

    // HTTP PUT 요청이 "/users/{id}" 경로로 들어오면 이 메소드가 호출
    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable String id, @RequestBody Map<String, Object> user) {
        return userService.updateUser(id, user);
    }

    // HTTP DELETE 요청이 "/users/{id}" 경로로 들어오면 이 메소드가 호출
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // HTTP POST 요청이 "/users/bulk" 경로로 들어오면 이 메소드가 호출
    @PostMapping("/users/bulk")
    public List<Integer> createUsers(@RequestBody List<Map<String, Object>> users) {
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
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
                .orElse("NONE");

        Map<String, String> response = new java.util.HashMap<>();
        response.put("username", username);
        response.put("role", role);

        return ResponseEntity.ok(response);
    }

    /**
     * [신규/교체] 특정 사용자의 역할을 변경하는 API 엔드포인트입니다.
     * 프론트엔드로부터 사용자 ID와 새로운 역할 ID 목록을 받아 처리합니다.
     * @param id 변경할 사용자의 ID
     * @param payload 프론트에서 보낸 JSON 데이터. {"roleIds": [1, 2]} 형태를 기대합니다.
     */
    @PutMapping("/api/users/{id}/roles") // 프론트엔드에서 호출할 새 주소입니다.
    public ResponseEntity<Void> updateUserRoles(@PathVariable("id") Integer id, @RequestBody Map<String, List<Integer>> payload) {
        // JSON 데이터에서 "roleIds" 라는 키로 역할 ID 리스트를 꺼냅니다.
        List<Integer> roleIds = payload.get("roleIds");

        if (roleIds == null) {
            // 잘못된 요청이라는 의미로 400 Bad Request 응답을 보냅니다.
            return ResponseEntity.badRequest().build();
        }

        // 이전에 수정한 서비스 메소드를 호출하여 역할을 변경합니다.
        userService.updateUserRoles(id, roleIds);

        // 성공적으로 처리되었음을 알리는 200 OK 응답을 보냅니다.
        return ResponseEntity.ok().build();
    }
}