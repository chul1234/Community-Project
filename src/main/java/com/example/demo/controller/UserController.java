package com.example.demo.controller;

import com.example.demo.service.user.IUserService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors; // Collectors import 추가

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority; // GrantedAuthority import 추가
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    @Autowired
    private IUserService userService;

    // ... (getAllUsers, getUser 등 다른 메소드들은 그대로 유지) ...
    @GetMapping("/users")
    public List<Map<String, Object>> getAllUsers() {
        return userService.findAllUsers();
    }
    
    @GetMapping("/users/{userId}")
    public Map<String, Object> getUser(@PathVariable String userId) {
        return userService.getUser(userId);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> user) {
        return userService.createUser(user);
    }

    @PutMapping("/users/{userId}")
    public Map<String, Object> updateUser(@PathVariable String userId, @RequestBody Map<String, Object> user) {
        return userService.updateUser(userId, user);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/bulk")
    public List<Integer> createUsers(@RequestBody List<Map<String, Object>> users) {
        return userService.createUsers(users);
    }


    // ▼▼▼▼▼▼▼▼▼▼▼▼ [수정] /api/me 메소드 ▼▼▼▼▼▼▼▼▼▼▼▼
    @GetMapping("/api/me")
    public ResponseEntity<Map<String, String>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        // [핵심 수정] 사용자가 가진 모든 역할을 리스트로 가져옵니다.
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList());

        // 'ADMIN' 역할이 있는지 확인하고, 있으면 대표 역할로 설정합니다.
        String primaryRole = "NONE";
        if (roles.contains("ADMIN")) {
            primaryRole = "ADMIN";
        } else if (!roles.isEmpty()) {
            primaryRole = roles.get(0); // ADMIN이 없으면 첫 번째 역할을 대표로 설정
        }

        Map<String, String> response = new java.util.HashMap<>();
        response.put("username", username);
        response.put("role", primaryRole); // 대표 역할을 프론트엔드에 전달

        return ResponseEntity.ok(response);
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ [수정] /api/me 메소드 ▲▲▲▲▲▲▲▲▲▲▲▲


    @PutMapping("/api/users/{userId}/roles")
    public ResponseEntity<Void> updateUserRoles(@PathVariable String userId, @RequestBody Map<String, List<String>> payload) {
        List<String> roleIds = payload.get("roleIds");

        if (roleIds == null) {
            return ResponseEntity.badRequest().build();
        }

        userService.updateUserRoles(userId, roleIds);
        return ResponseEntity.ok().build();
    }
}