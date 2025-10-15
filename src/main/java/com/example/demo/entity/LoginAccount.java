package com.example.demo.entity;

public class LoginAccount { //loginaccount 테이블과 매핑되는 엔티티 클래스

    private Long id; //loginaccount 테이블의 기본 키
    private Long userId; //user 테이블의 외래 키
    private String email; //사용자 이메일
    private String password; //사용자 비밀번호
    private String role; //사용자 역할 (예: USER, ADMIN)

    public Long getId() { //필드 id값 읽어서 반환 게터
        return id; 
    }

    public void setId(Long id) { //필드 id값 설정(변경)하는 세터
        this.id = id;
    }

    public Long getUserId() { //필드 userId값 읽어서 반환 게터
        return userId;
    }

    public void setUserId(Long userId) { //필드 userId값 설정(변경)하는 세터
        this.userId = userId;
    }

    public String getEmail() { //필드 email값 읽어서 반환 게터
        return email;
    }

    public void setEmail(String email) { //필드 email값 설정(변경)하는 세터
        this.email = email;
    }

    public String getPassword() { //필드 password값 읽어서 반환 게터
        return password;
    }

    public void setPassword(String password) { //필드 password값 설정(변경)하는 세터
        this.password = password;
    }

    public String getRole() { //필드 role값 읽어서 반환 게터
        return role;
    }

    public void setRole(String role) { //필드 role값 설정(변경)하는 세터
        this.role = role;
    }
}