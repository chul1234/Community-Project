package com.example.demo.config;

import org.springframework.context.annotation.Bean; // @Bean 어노테이션 사용
import org.springframework.context.annotation.Configuration; // @Configuration 어노테이션 사용
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // HttpSecurity: 웹 보안 설정을 위한 핵심 클래스
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // @EnableWebSecurity: Spring Security 활성화
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCryptPasswordEncoder: BCrypt 암호화 구현체
import org.springframework.security.crypto.password.PasswordEncoder; // PasswordEncoder: 비밀번호 암호화 인터페이스
import org.springframework.security.web.SecurityFilterChain; // SecurityFilterChain: 보안 필터 체인 정의

@Configuration // @Configuration: 이 파일이 Spring 설정 파일임을 선언
@EnableWebSecurity // @EnableWebSecurity: Spring Security 기능 활성화
public class SecurityConfig { // SecurityConfig: 웹 보안 설정을 위한 메인 클래스

    @Bean // @Bean: 이 메소드가 반환하는 객체를 Spring Bean으로 등록
    public PasswordEncoder passwordEncoder() { // passwordEncoder(): 비밀번호 암호화기(PasswordEncoder) 생성 메소드
        // BCryptPasswordEncoder: 강력한 해시 암호화 방식인 BCrypt 사용
        return new BCryptPasswordEncoder();
    }

    @Bean
    // filterChain(): HTTP 요청에 대한 보안 필터 체인 설정 메소드. (HttpSecurity 객체 사용)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http // http: HttpSecurity 객체를 통한 설정 시작
            // 1. CSRF(사이트 간 요청 위조) 보안 기능 비활성화. (csrf().disable() 메소드)
            .csrf(csrf -> csrf.disable())

            // 2. URL별 접근 권한 설정 시작. (authorizeHttpRequests() 메소드)
            .authorizeHttpRequests(auth -> auth
                // .requestMatchers(): 특정 경로(URL) 지정.
                // [수정됨]: "/api/menus" 추가 (메뉴 API)
                .requestMatchers(
                    "/api/menus", // <-- 메뉴 API 경로 추가
                    "/login", 
                    "/register", 
                    "/css/**",
                    "/lib/**", 
                    "/*.js", 
                    "/views/**", 
                    "/*.html"
                )
                .permitAll() // .permitAll(): 위 경로들에 대해 모든 사용자(비로그인 포함)의 접근 허용
                // .anyRequest(): 위에서 지정한 경로 외의 모든 요청
                .anyRequest().authenticated() // .authenticated(): 반드시 인증(로그인)된 사용자만 접근 허용
            )

            // 3. 폼 기반 로그인 설정 시작. (formLogin() 메소드)
            .formLogin(form -> form
                // .loginPage(): 사용할 커스텀 로그인 페이지 URL 지정
                .loginPage("/login") //
                // .loginProcessingUrl(): 실제 로그인 처리를 수행할 URL (login.html의 form action과 일치)
                .loginProcessingUrl("/login") //
                // .usernameParameter(): 로그인 ID로 사용할 파라미터 이름 (login.html의 input name과 일치)
                .usernameParameter("username") //
                // .defaultSuccessUrl(): 로그인 성공 시 이동할 기본 URL. (true: 항상 이 URL로 이동)
                .defaultSuccessUrl("/#!/bus", true) //
                .permitAll() // .permitAll(): 로그인 페이지 자체는 모든 사용자가 접근 가능
            )

            // 4. 로그아웃 설정 시작. (logout() 메소드)
            .logout(logout -> logout
                // .logoutUrl(): 로그아웃을 처리할 URL (Angular_http.html의 로그아웃 링크와 일치)
                .logoutUrl("/logout") //
                // .logoutSuccessUrl(): 로그아웃 성공 후 이동할 URL
                .logoutSuccessUrl("/login")
            );

        // .build(): 설정 완료된 SecurityFilterChain 객체 생성 및 반환
        return http.build();
    }
}