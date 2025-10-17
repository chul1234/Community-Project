package com.example.demo.config;
//bean 어노테이션 사용
import org.springframework.context.annotation.Bean;
//configuration 어노테이션 사용, security 설정을 위한 어노테이션 사용
import org.springframework.context.annotation.Configuration;
//HttpSecurity사용하기 위한 import, filterChaindptj CSRF, URL접근권한, 로그인, 로그아웃 설정
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//EnableWebSecurity: Spring Security를 활성화
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//BCryptPasswordEncoder 클래스를 사용, pring Security가 제공하는 여러 비밀번호 암호화 방식 중 하나인 BCrypt 알고리즘,passwordEncoder() 메소드에서 이 클래스의 객체를 생성, 반환
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//PasswordEncoder 인터페이스(일종의 규격)를 사용
import org.springframework.security.crypto.password.PasswordEncoder;
//SecurityFilterChain 클래스를 사용
import org.springframework.security.web.SecurityFilterChain;

@Configuration // 설정 파일임을 나타내는 어노테이션
@EnableWebSecurity // Spring Security 활성화 어노테이션
public class SecurityConfig { // 웹 보안 설정을 담기 위한 클래스 정의

    @Bean // 반환하는 객체 spring이 관리하는 bean으로 등록
    public PasswordEncoder passwordEncoder() { // 비밀번호 암호화에 사용할 PasswordEncoder 빈을 생성하는 메소드
        return new BCryptPasswordEncoder(); // BCryptPasswordEncoder spring security에서 제공, 암호화방식
    }

    @Bean
    //URL 접근 권한, 로그인/로그아웃 정책 등 대부분의 보안 규칙
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http // SecurityFilterChain 객체를 생성하기 위한 HttpSecurity 객체
        // SecurityFilterChain : , 웹사이트로 들어오는 모든 방문객(요청)을 검사
            // 1. CSRF 보안 기능 비활성화
            .csrf(csrf -> csrf.disable())

            // 2. URL 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 메인 페이지('/')를 목록에서 제거하여, 로그인해야만 접근 가능하도록 변경합니다.
                // 이제 로그인, 회원가입 관련 URL과 웹페이지 리소스만 누구나 접근할 수 있습니다.
                .requestMatchers("/login", "/register", "/css/**","/lib/**", "/*.js", "/views/**", "/*.html").permitAll()
                // 그 외 모든 요청(이제 메인 페이지'/' 포함)은 반드시 로그인을 해야만 접근 가능
                .anyRequest().authenticated() // 인증된 사용자만 접근 허용
            )

            // 3. 로그인 페이지 설정
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                // ★★★ 로그인 ID로 사용할 파라미터 이름을 명시합니다. ★★★
                .usernameParameter("username") 
                .defaultSuccessUrl("/", true)
                .permitAll()
            )

            // 4. 로그아웃 설정
            .logout(logout -> logout
                .logoutUrl("/logout") // 로그아웃 처리 URL
                .logoutSuccessUrl("/login") // 로그아웃 성공 후 이동할 URL
            );

        return http.build(); // 설정이 완료된 SecurityFilterChain 객체를 반환
    }
}
