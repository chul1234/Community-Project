package com.example.demo.service.user;

import java.util.List; // List import 추가(데이터를 순서대로 저장)
import java.util.Map; // Map import 추가(키-값 쌍으로 데이터 저장)

public interface IUserService { //IUserService라는 인터페이스 선언
    Map<String, Object> getUser(String id); //Map<String, Object> 타입을 반환하는 getUser 메소드 선언
    Map<String, Object> createUser(Map<String, Object> user); //Map<String, Object> 타입을 반환하는 createUser 메소드 선언
    Map<String, Object> updateUser(String id, Map<String, Object> user); //Map<String, Object> 타입을 반환하는 updateUser 메소드 선언
    void deleteUser(String id); //void 타입을 반환하는 deleteUser 메소드 선언(삭제후 변환할 데이터 X)
    List<Map<String, Object>> findAllUsers(); //List<Map<String, Object>> 타입을 반환하는 findAllUsers 메소드 선언
    List<Integer> createUsers(List<Map<String, Object>> users); //List<Integer> 타입을 반환하는 createUsers 메소드 선언,list를 받아서 db저장 
    void updateUserRole(Integer userId, Integer roleId);
}