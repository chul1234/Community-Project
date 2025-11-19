package com.example.demo.dao;

import java.io.InputStream; // 파일을 바이트 스트림으로 읽기 위해 필요한 클래스
import java.util.Properties; //properties 형식의 파일(키=값 이루어진 파일)을 쉽게 다루기 위한 클래스

public class SqlLoader {
    private static final Properties sqls = new Properties();

    static { //static 초기화 블록 : 클래스가 처음 로드될 때 한 번 실행
        //sql.properties 파일을 읽어서 Properties 객체에 로드
        try (InputStream input = SqlLoader.class.getClassLoader().getResourceAsStream("sql.properties")) {
            sqls.load(input); //sql가 input스트림을 통해 파일을 읽어들여, 키=값쌍을 자의 내부에 저장
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getSql(String key) { //key에 해당하는 SQL문을 반환하는 메소드
        return sqls.getProperty(key); //Properties 객체에서 key에 해당하는 값을 반환
    }
}