package com.example.demo.service.menu.impl; // (새 패키지)

import com.example.demo.dao.MenuDAO;
import com.example.demo.service.menu.IMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuServiceImpl implements IMenuService {

    @Autowired
    private MenuDAO menuDAO;

    @Override
    public List<Map<String, Object>> getGroupedMenus() {
        // 1. DAO를 통해 DB에서 정렬된 '평평한' 메뉴 목록을 가져옵니다.
        List<Map<String, Object>> flatList = menuDAO.findAllSorted();

        // 2. 최종 반환할 대메뉴 목록 (계층 구조의 최상위)
        List<Map<String, Object>> mainMenus = new ArrayList<>();
        
        // 3. 소메뉴들을 부모 ID별로 임시 저장할 Map
        // (Key: parent_id, Value: 해당 부모에 속한 소메뉴 리스트)
        Map<String, List<Map<String, Object>>> subMenuMap = new HashMap<>();

        // 4. 평평한 목록을 순회하며 대메뉴와 소메뉴를 분리합니다.
        for (Map<String, Object> menu : flatList) {
            int depth = (Integer) menu.get("depth");
            
            if (depth == 1) {
                // Depth 1 = 대메뉴
                // 대메뉴는 'subMenus'라는 빈 리스트를 미리 추가해 둡니다.
                menu.put("subMenus", new ArrayList<>());
                mainMenus.add(menu);
            } else if (depth == 2) {
                // Depth 2 = 소메뉴
                String parentId = (String) menu.get("parent_id");
                
                // subMenuMap에서 해당 부모 ID의 리스트를 찾거나, 없으면 새로 만듭니다.
                // .computeIfAbsent() : parentId가 맵에 없으면, 새 ArrayList를 만들어서 맵에 넣고 그 리스트를 반환
                List<Map<String, Object>> subList = subMenuMap.computeIfAbsent(parentId, k -> new ArrayList<>());
                
                // 해당 부모의 리스트에 현재 소메뉴를 추가합니다.
                subList.add(menu);
            }
        }

        // 5. 분리 작업이 끝난 후, 대메뉴 목록(mainMenus)을 다시 순회합니다.
        for (Map<String, Object> mainMenu : mainMenus) {
            // 대메뉴의 ID (예: 'menu_board')
            String menuId = (String) mainMenu.get("menu_id");
            
            // subMenuMap에서 해당 ID를 부모로 가지는 소메뉴 리스트를 찾습니다.
            List<Map<String, Object>> subMenus = subMenuMap.get(menuId);
            
            if (subMenus != null) {
                // 찾은 소메뉴 리스트를 대메뉴의 'subMenus' 키에 덮어씁니다.
                mainMenu.put("subMenus", subMenus);
            }
        }

        // 6. 소메뉴가 포함된 대메뉴 리스트를 반환합니다.
        return mainMenus;
    }
}