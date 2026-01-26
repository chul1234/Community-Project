package com.example.demo.controller;

import org.springframework.stereotype.Controller; // 컨트롤러 어노테이션
import org.springframework.web.bind.annotation.GetMapping; // GetMapping 어노테이션

@Controller // 이 클래스가 스프링 MVC의 컨트롤러임을 나타내는 어노테이션
public class ViewController { // ViewController 클래스 선언
    @GetMapping("/") // '/' 주소로 접속하면 메인 페이지를 보여줍니다.
    public String mainPage(jakarta.servlet.http.HttpServletRequest request) {
        
        // 1. Cloudflare가 붙여준 '진짜 IP' 확인
        String realIp = request.getHeader("CF-Connecting-IP");

        // 2. 없으면 X-Forwarded-For (프록시/로드밸런서) 확인
        if (realIp == null || realIp.isEmpty()) {
            realIp = request.getHeader("X-Forwarded-For");
        }

        // 3. 그래도 없으면 기본 request IP
        if (realIp == null || realIp.isEmpty()) {
            realIp = request.getRemoteAddr();
        }

        // 4. 눈에 띄게 콘솔 출력
        System.out.println("\n");
        System.out.println("=============================================");
        System.out.println(">>> 🕵️‍♂️ [NEW VISITOR] 접속 IP 확인 : " + realIp);
        System.out.println("=============================================");
        System.out.println("\n");

        // "forward:"를 제거하면 "Angular_http.html"이라는 뷰를 찾으라는 의미가 됨
        return "Angular_http.html"; //이때 로그인 우뮤 String X -> modelAndview를 사용해서 .html 말고 다른 정보도 같이 보내라
    }

    // '/login' 주소로 접속하면 로그인 페이지를 보여줍니다.
    @GetMapping("/login")
    public String loginForm() {
        return "login"; // thymeleaf템플릿 엔진 사용 -> templates/login.html 
    }

    // '/register' 주소로 접속하면 회원가입 페이지를 보여줍니다.
    @GetMapping("/register")
    public String registerForm() {
        return "register"; // thymeleaf템플릿 엔진 사용 ->  templates/register.html
    }
}