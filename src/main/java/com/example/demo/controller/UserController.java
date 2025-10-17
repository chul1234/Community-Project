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

//JSON 같은 순수 데이터로 응답하는 컨트롤러
@RestController 
public class UserController {
    //@Autowired: Spring이 미리 만들어 둔 IUserService 타입의 객체
    @Autowired
    private IUserService userService;
    
    //@GetMapping("/users"): HTTP GET 방식으로 '/users' 주소에 요청이 오면 이 메소드를 실행
    @GetMapping("/users")
    public List<Map<String, Object>> getAllUsers() {
        //userService에게 모든 사용자 찾아달라고 요청, 결과 웹에 표시
        return userService.findAllUsers();
    }
    // @GetMapping("/users/{userId}"): HTTP GET 방식으로 '/users/{userId}' 주소에 요청이 오면 이 메소드를 실행
    @GetMapping("/users/{userId}")
    //@PathVariable String userId: URL의 {userId} 부분을 String 타입의 userId 변수에 담음
    public Map<String, Object> getUser(@PathVariable String userId) {
        //받은 userId로 userService에 전달 사용자 정보 반환
        return userService.getUser(userId);
    }

    @PostMapping("/users")
    //@RequestBody Map<String, Object> user: 요청의 본문(body)에 담겨온 JSON 데이터를 Map 형태로 변환
    public Map<String, Object> createUser(@RequestBody Map<String, Object> user) {
        // 받은 사용자 정보를 userService에 전달하여 사용자를 생성하고, 그 결과를 반환
        return userService.createUser(user);
    }

    @PutMapping("/users/{userId}")
    public Map<String, Object> updateUser(@PathVariable String userId, @RequestBody Map<String, Object> user) {
        // URL에서 받은 userId와 요청 본문의 수정 정보를 userService에 전달하여 업데이트
        return userService.updateUser(userId, user);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        // userService에게 해당 userId의 사용자 삭제를 요청합니다.
        userService.deleteUser(userId);
        // 작업이 성공적으로 끝났고, 별도로 보낼 데이터가 없다는 의미의 '204 No Content' 응답
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/bulk")
    public List<Integer> createUsers(@RequestBody List<Map<String, Object>> users) {
        // 사용자 정보가 담긴 '리스트'를 통째로 userService에 전달하여 처리
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
        //authentication.getAuthorities() 사용자가 가진 모든 권한 불러옴
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList());
        // 가장 높은 권한 하나만 선택하여 응답에 포함시킵니다.
        String primaryRole = "NONE";
        if (roles.contains("ADMIN")) {
            primaryRole = "ADMIN";
        } else if (!roles.isEmpty()) {
            primaryRole = roles.get(0);
        }
        // 응답에 사용자 이름과 역할 정보를 포함한 Map을 생성합니다.
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("username", username);
        response.put("role", primaryRole);
        // [핵심] 응답에 사용자의 실제 이름(name)을 추가합니다.
        response.put("name", userDetails != null ? userDetails.get("name") : username);
        // 필요에 따라 추가 사용자 정보도 여기에 포함할 수 있습니다.
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/users/{userId}/roles")
    public ResponseEntity<Void> updateUserRoles(@PathVariable String userId, @RequestBody Map<String, List<String>> payload) {
        // 요청 본문에서 'roleIds' 리스트를 추출합니다.
        List<String> roleIds = payload.get("roleIds");
        // roleIds가 null인 경우 잘못된 요청으로 간주하고 400 Bad Request 응답을 반환합니다.
        if (roleIds == null) {
            return ResponseEntity.badRequest().build();
        }
        // userService를 통해 해당 사용자의 역할을 업데이트합니다.
        userService.updateUserRoles(userId, roleIds);
        return ResponseEntity.ok().build();
    }
    @DeleteMapping("/api/users/me")
    public ResponseEntity<Void> deleteCurrentUser(Authentication authentication) {
        // 1. 현재 로그인한 사용자의 ID(username)를 가져옵니다.
        String currentUserId = authentication.getName();
        // 2. UserService를 통해 해당 사용자를 삭제합니다.
        userService.deleteUser(currentUserId);
        // 3. 성공적으로 삭제되었음을 알리는 응답을 보냅니다.
        return ResponseEntity.ok().build();
    }
}
