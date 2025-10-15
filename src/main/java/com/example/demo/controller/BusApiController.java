package com.example.demo.controller; //ipp.demo에 속해있다.
import org.springframework.web.bind.annotation.CrossOrigin; //데이터 요청을 허용
import org.springframework.web.bind.annotation.GetMapping; //get요청 주소와 특정 자바 메소드 연결
import org.springframework.web.bind.annotation.RestController; //데이터 반환하는 API
import org.springframework.web.client.RestTemplate; //스프링 HTTP 통신 도구

@RestController //데이터 자체를 HTTP 응답 전송
public class BusApiController { //클래스 정의
    //CORS(Cross-origin-Resource-Sharing)보안 정책 문제를 해결해주는 어노테이션-> 도메인에서도 API 호출 허용
    @CrossOrigin
    //api/but-stops라는 경로로 요청을 보내면 바로 getBusStops() 메서드 실행
    @GetMapping("/api/bus-stops")
    public String getBusStops() { //문자열 변환
        // RestTemplate -> 외부 API와 통신하기 위한 도구(다른 서버에 데이터 요청)
        RestTemplate restTemplate = new RestTemplate();
        
        // API 요청을 위한 URL
        String baseUrl = "https://apis.data.go.kr/6270000/dbmsapi02/getBasic02";
        String serviceKey = "5cedc5eab7543fd67b6d3bcc0d35d2851975c4577afae580e32f0ac0d391b255";
        String url = baseUrl + "?serviceKey=" + serviceKey + "&type=json";
        
        // API 서버에 GET 요청을 보내고, 응답을 문자열(JSON) 형태로 받음
        return restTemplate.getForObject(url, String.class); //getForObject -> 특정 자바 객체 형태로 변환
    }
} 