package com.example.demo.service.menu.impl; // (새 패키지)

import com.example.demo.dao.MenuDAO; // DB의 menus 테이블과 통신하는 MenuDAO import
import com.example.demo.service.menu.IMenuService; // IMenuService 인터페이스(설계도) import
import org.springframework.beans.factory.annotation.Autowired; // Spring의 @Autowired (의존성 주입) 기능 사용
import org.springframework.stereotype.Service; // Spring의 @Service (서비스 계층) 선언

import java.util.ArrayList; // ArrayList: 가변 크기 리스트를 만들기 위해 import
import java.util.HashMap; // HashMap: 키-값 쌍의 맵(보관함)을 만들기 위해 import
import java.util.List; // List 인터페이스 import
import java.util.Map; // Map 인터페이스 import

@Service // 이 클래스가 Spring의 서비스 컴포넌트임을 선언
public class MenuServiceImpl implements IMenuService {

    @Autowired // Spring이 MenuDAO 타입의 객체(Bean)를 자동으로 주입
    private MenuDAO menuDAO;

    /**
     * "평평한" 메뉴 목록(DB)을 "계층형" 구조(JSON)로 가공하는 메소드
     */
    @Override // IMenuService 인터페이스의 getGroupedMenus 메소드를 구현
    public List<Map<String, Object>> getGroupedMenus() {
        
        // 1. DAO를 통해 DB에서 정렬된 '평평한' 메뉴 목록을 가져옴.
        // menuDAO의 findAllSorted() 메소드를 호출
        List<Map<String, Object>> flatList = menuDAO.findAllSorted();

        // 2. 최종 반환할 대메뉴(Depth 1) 목록을 담을 빈 리스트(mainMenus)를 생성
        // new ArrayList<>() 생성자 호출
        List<Map<String, Object>> mainMenus = new ArrayList<>();
        
        // 3. 소메뉴들을 부모 ID별로 임시 저장할 Map(보관함)을 생성
        // (Key: String 타입의 parent_id, Value: 해당 부모에 속한 소메뉴의 List)
        // new HashMap<>() 생성자 호출
        Map<String, List<Map<String, Object>>> subMenuMap = new HashMap<>();

        // 4. (1차 루프) 평평한 목록(flatList)을 순회하며 대메뉴와 소메뉴를 분리.
        // 향상된 for문(for-each loop) 사용
        for (Map<String, Object> menu : flatList) {
            
            // menu 맵에서 "depth" 키의 값을 .get() 메소드로 가져와 Integer로 형변환
            int depth = (Integer) menu.get("depth");
            
            // 4-1. 대메뉴(Depth 1)일 경우
            if (depth == 1) {
                // (중요) 프론트엔드에서 소메뉴 리스트(subMenus)를 참조할 수 있도록,
                // menu 맵에 .put() 메소드를 사용해 'subMenus'라는 키와 '빈 ArrayList'를 미리 추가
                menu.put("subMenus", new ArrayList<>());
                
                // 'subMenus' 키가 추가된 대메뉴 맵을
                // mainMenus 리스트의 .add() 메소드를 사용해 최종 결과 목록에 추가
                mainMenus.add(menu);
                
            // 4-2. 소메뉴(Depth 2)일 경우
            } else if (depth == 2) {
                // menu 맵에서 "parent_id" 키의 값을 .get() 메소드로 가져와 String으로 형변환
                String parentId = (String) menu.get("parent_id");
                
                // (핵심 로직) subMenuMap(보관함)에서 parentId 키에 해당하는 리스트
                // .computeIfAbsent() 메소드:
                //   - 만약 parentId 키가 맵에 존재하면: 해당 키의 값(소메뉴 리스트)을 즉시 반환
                //   - 만약 parentId 키가 맵에 *없으면*: 람다식(k -> new ArrayList<>())을 실행하여
                //     새로운 빈 리스트를 생성 -> 그 리스트를 맵에 parentId 키로 저장 -> 그리고 그 새 리스트를 반환
                List<Map<String, Object>> subList = subMenuMap.computeIfAbsent(parentId, k -> new ArrayList<>());
                
                // .add() 메소드를 사용해 현재 소메뉴(menu)를
                // 방금 찾거나 생성한 부모의 소메뉴 리스트(subList)에 추가
                subList.add(menu);
            }
        } // (1차 루프 종료)

        // 5. (2차 루프) 분리 작업이 끝난 후, 대메뉴 목록(mainMenus)을 다시 순회하여 소메뉴를 조립
        for (Map<String, Object> mainMenu : mainMenus) {
            
            // mainMenu 맵에서 "menu_id" 키의 값을 .get() 메소드로 가져옴 (예: "menu_board")
            String menuId = (String) mainMenu.get("menu_id");
            
            // subMenuMap(보관함)에서 "menu_board" 키에 해당하는 소메뉴 리스트를 .get() 메소드로 찾음
            List<Map<String, Object>> subMenus = subMenuMap.get(menuId);
            
            // 만약 subMenus가 null이 아니라면 (즉, 해당 대메뉴에 속한 소메뉴가 1개 이상 존재한다면)
            if (subMenus != null) {
                
                // mainMenu 맵의 'subMenus' 키의 값을 .put() 메소드를 사용해
                // (1차 루프에서 넣었던 '빈 리스트')를 (방금 찾은 '데이터가 든 리스트')로 덮어씀
                mainMenu.put("subMenus", subMenus);
            }
        } // (2차 루프 종료)

        // 6. 소메뉴가 포함된 계층형 대메뉴 리스트(mainMenus)를 반환
        return mainMenus;
    }
}