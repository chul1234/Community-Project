package com.example.demo.controller;

import com.example.demo.service.user.IUserService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    @Autowired
    private IUserService userService;

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

    @GetMapping("/api/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() { // 반환 타입을 Map<String, String>에서 Map<String, Object>로 변경
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        // [핵심] DB에서 user_id로 사용자 전체 정보를 가져옵니다.
        Map<String, Object> userDetails = userService.getUser(username);
        
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList());

        String primaryRole = "NONE";
        if (roles.contains("ADMIN")) {
            primaryRole = "ADMIN";
        } else if (!roles.isEmpty()) {
            primaryRole = roles.get(0);
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("username", username);
        response.put("role", primaryRole);
        // [핵심] 응답에 사용자의 실제 이름(name)을 추가합니다.
        response.put("name", userDetails != null ? userDetails.get("name") : username);

        return ResponseEntity.ok(response);
    }

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