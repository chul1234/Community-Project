package com.example.demo.controller;

import org.springframework.stereotype.Controller; // 컨트롤러 어노테이션
import org.springframework.web.bind.annotation.GetMapping; // GetMapping 어노테이션

@Controller // 이 클래스가 스프링 MVC의 컨트롤러임을 나타내는 어노테이션
public class ViewController { // ViewController 클래스 선언
    @GetMapping("/") // '/' 주소로 접속하면 메인 페이지를 보여줍니다.
    public String mainPage() { 
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