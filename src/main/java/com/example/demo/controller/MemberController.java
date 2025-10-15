package com.example.demo.controller;

import com.example.demo.service.user.impl.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class MemberController { 

    @Autowired // 의존성 주입
    private MemberService memberService; // MemberService 주입

    @PostMapping("/register") // 회원가입 폼 제출 처리
    // @RequestParam Map<String, String> userParams: 폼 데이터(이름, 이메일, 비밀번호 등)를 Map 형태로 받음
    public String registerUser(@RequestParam Map<String, String> userParams) {
        memberService.register(userParams); // 회원가입 서비스 호출
        return "redirect:/login"; // 회원가입 완료 후 로그인 페이지로 이동
    }
}