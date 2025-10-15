package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // 스프링 부트의 자동 설정, 스프링 Bean 읽기와 생성 모두 자동으로 설정
public class DemoApplication { 
    public static void main(String[] args) { 
        SpringApplication.run(DemoApplication.class, args);
    }
}