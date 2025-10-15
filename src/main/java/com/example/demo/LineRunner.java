package com.example.demo;

// UserDTO import 문을 제거하고, Map과 HashMap을 import 합니다.
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.demo.service.user.IUserService;

@Component
public class LineRunner implements CommandLineRunner {

    @Autowired
    private IUserService userService;

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- 작업 선택 (숫자 입력) ---");
            System.out.println("1: 사용자 추가");
            System.out.println("2: 사용자 수정");
            System.out.println("3: 사용자 삭제");
            System.out.println("4: 전체 사용자 조회");
            System.out.println("exit: 종료");
            System.out.print(" > ");

            String choice = scanner.nextLine();

            if ("exit".equalsIgnoreCase(choice)) {
                break;
            }

            switch (choice) {
                case "1":
                    createUser(scanner);
                    break;
                case "2":
                    updateUser(scanner);
                    break;
                case "3":
                    deleteUser(scanner);
                    break;
                case "4":
                    showAllUsers();
                    break;
                default:
                    System.out.println("잘못된 입력입니다. 다시 선택해주세요.");
                    break;
            }
        }
        System.out.println("--- 프로그램 종료 ---");
    }

    // 사용자 추가
    private void createUser(Scanner scanner) {
        System.out.println("\n--- 사용자 추가 ---");
        System.out.print("이름: ");
        String name = scanner.nextLine();
        System.out.print("전화번호: ");
        String phone = scanner.nextLine();
        System.out.print("이메일: ");
        String email = scanner.nextLine();

        // UserDTO 대신 Map 객체를 생성하고 데이터를 저장합니다.
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("name", name);
        newUser.put("phone", phone);
        newUser.put("email", email);

        userService.createUser(newUser);
        System.out.println("✅ 사용자 추가가 완료되었습니다!");
        showAllUsers();
    }

    // 사용자 수정
    private void updateUser(Scanner scanner) {
        System.out.println("\n--- 사용자 수정 ---");
        showAllUsers();
        System.out.print("수정할 사용자의 ID를 입력하세요: ");
        String id = scanner.nextLine();

        if (userService.getUser(id) == null) {
            System.out.println("❌ 해당 ID의 사용자가 없습니다.");
            return;
        }

        System.out.print("새로운 이름: ");
        String newName = scanner.nextLine();
        System.out.print("새로운 전화번호: ");
        String newPhone = scanner.nextLine();
        System.out.print("새로운 이메일: ");
        String newEmail = scanner.nextLine();

        // UserDTO 대신 Map 객체를 생성하고 데이터를 저장합니다.
        Map<String, Object> updatedUser = new HashMap<>();
        updatedUser.put("name", newName);
        updatedUser.put("phone", newPhone);
        updatedUser.put("email", newEmail);

        userService.updateUser(id, updatedUser);
        System.out.println("✅ 사용자 수정이 완료되었습니다!");
        showAllUsers();
    }

    // 사용자 삭제
    private void deleteUser(Scanner scanner) {
        System.out.println("\n--- 사용자 삭제 ---");
        showAllUsers();
        System.out.print("삭제할 사용자의 ID를 입력하세요: ");
        String id = scanner.nextLine();

        if (userService.getUser(id) == null) {
            System.out.println("해당 ID의 사용자가 없습니다.");
            return;
        }

        userService.deleteUser(id);
        System.out.println("✅ 사용자 삭제가 완료되었습니다!");
        showAllUsers();
    }

    // 전체 사용자 조회
    private void showAllUsers() {
        System.out.println("\n--- 현재 DB 전체 사용자 목록 ---");
        // 반환 타입을 List<Map<String, Object>>로 변경합니다.
        List<Map<String, Object>> userList = userService.findAllUsers();
        if (userList.isEmpty()) {
            System.out.println(" > 데이터가 없습니다.");
        } else {
            // Map 객체의 toString() 메소드가 자동으로 호출되어 보기 좋게 출력됩니다.
            userList.forEach(user -> System.out.println(" > " + user));
        }
        System.out.println("---------------------------------");
    }
}