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

    @GetMapping("/users")
    public List<Map<String, Object>> getAllUsers() {
        return userService.findAllUsers();
    }
    
    // [수정] URL 경로 변수를 userId로 명확히 하고 String으로 받습니다.
    @GetMapping("/users/{userId}")
    public Map<String, Object> getUser(@PathVariable String userId) {
        return userService.getUser(userId);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> user) {
        return userService.createUser(user);
    }

    // [수정] URL 경로 변수를 userId로 명확히 하고 String으로 받습니다.
    @PutMapping("/users/{userId}")
    public Map<String, Object> updateUser(@PathVariable String userId, @RequestBody Map<String, Object> user) {
        return userService.updateUser(userId, user);
    }

    // [수정] URL 경로 변수를 userId로 명확히 하고 String으로 받습니다.
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
     * [수정] 역할 변경 API의 모든 파라미터를 String 타입으로 변경합니다.
     */
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