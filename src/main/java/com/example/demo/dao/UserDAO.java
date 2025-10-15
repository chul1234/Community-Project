package com.example.demo.dao;

//spring의 의존성을 주입 기능
import org.springframework.beans.factory.annotation.Autowired;
//DAO임을 spring에게 알림
import org.springframework.stereotype.Repository;
//Datasource사용
import javax.sql.DataSource;
//JDBC API사용을 하기 위한 import
import java.sql.*;
import java.util.ArrayList; // ArrayList import 추가
import java.util.HashMap; // HashMap import 추가
import java.util.List; // List import 추가
import java.util.Map; // Map import 추가
import java.util.Optional; // Optional import 추가

@Repository
public class UserDAO { // DAO 클래스
    @Autowired 
    private DataSource dataSource; // DataSource 주입
    private Connection getConnection() throws SQLException { 
        return dataSource.getConnection(); // 주입 받은 dataSource에서 실제 데이터베이스 연결 객체를 가져와 반환
    }

    /**
     * [수정됨] CREATE(INSERT) 또는 UPDATE를 수행하고, 성공 여부를 반환합니다.
     * INSERT 성공 시: 1 반환 (user Map에는 새로 생성된 ID가 포함됨)
     * UPDATE 성공 시: 1 반환
     * 실패 또는 영향받은 행이 없을 시: 0 반환
     */

    // CREATE 및 UPDATE 기능(성공 실패던져서 확인 쿼리 결과)
    // DTO 대신 Map사용, save(Map<String, Object> user)입력값, Map<String, Object> 결과값
    // Map key: 컬럼명, value: 컬럼값 하나로 묶음, save메소드 선언
    
    public int save(Map<String, Object> user) { 
        // user Map에 id가 없거나 0이면 INSERT, 있으면 UPDATE
        if (user.get("id") == null || (Integer) user.get("id") == 0) {
            // INSERT
            String sql = SqlLoader.getSql("user.insert");
            // try-with-resources : try 블록이 끝나면 conn과 pstmt 객체가 자동으로 닫힙니다(자원 해제).
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, (String) user.get("name"));
                pstmt.setString(2, (String) user.get("phone"));
                pstmt.setString(3, (String) user.get("email"));
                
                int affectedRows = pstmt.executeUpdate(); // 쿼리 실행 및 영향받은 행의 수 저장

                if (affectedRows > 0) { // 1개 이상의 행이 추가되었다면 성공
                    try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            // 서비스 계층에서 활용할 수 있도록 원본 user Map에 ID를 설정해 줍니다.
                            user.put("id", generatedKeys.getInt(1));
                        }
                    }
                }
                return affectedRows; // 성공 시 1, 실패 시 0 반환
            } catch (SQLException e) {
                e.printStackTrace();
                return 0; // 예외 발생 시 0 반환
            }
        } else {
            // UPDATE
            String sql = SqlLoader.getSql("user.update");
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, (String) user.get("name"));
                pstmt.setString(2, (String) user.get("phone"));
                pstmt.setString(3, (String) user.get("email"));
                pstmt.setInt(4, (Integer) user.get("id"));

                // UPDATE 쿼리로 인해 영향받은 행의 수(성공 시 1, 대상 없으면 0)를 그대로 반환
                // ▼▼▼ [오류 수정] 이 부분은 이미 int를 반환하고 있었으므로, 선언부만 고치면 됩니다. ▼▼▼
                return pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                // ▼▼▼ [오류 수정] 반환 타입을 int로 통일했습니다. ▼▼▼
                return 0; // 예외 발생 시 0 반환
            }
        }
    }

    /**
     * [수정됨] ID로 사용자를 삭제하고, 성공 여부를 반환합니다.
     * @return 삭제된 행의 수 (성공: 1, 대상 없음 또는 실패: 0)
     */
    public int deleteById(Integer id) {
        String sql = SqlLoader.getSql("user.delete.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            // 삭제된 행의 수(성공 시 1, 대상 없으면 0)를 반환
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0; // 예외 발생 시 0 반환
        }
    }

    // READ (ID로 조회) 기능
    // optional<UserDTO> : 반환 타입 사용, 조회 결과가 있을수도 없을 수도 있다. 
    public Optional<Map<String, Object>> findById(Integer id) {
        String sql = SqlLoader.getSql("user.select.by_id");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // SQL문의 첫 번째 '?'에 파라미터로 받은 id를 설정합니다.
            pstmt.setInt(1, id);
            // SELECT 문을 실행하고, 그 결과를 ResultSet 객체(rs)로 받습니다.
            try (ResultSet rs = pstmt.executeQuery()) {
                // 결과 데이터가 있는지 확인합니다. (데이터가 있으면 rs.next()는 true를 반환합니다)
                if (rs.next()) { //List<UserDTO> 타입을 반환하는 findAll 메소드 선언
                    //user라는 변수에 Map객체 생성, HashMap객체를 생성, put으로 데이터 저장
                    Map<String, Object> user = new HashMap<>(); // HashMap 생성
                    user.put("id", rs.getInt("id"));             // put으로 데이터 저장
                    user.put("name", rs.getString("name"));
                    user.put("phone", rs.getString("phone"));
                    user.put("email", rs.getString("email"));
                    return Optional.of(user); // DB 조회된 데이터 O -> map을 만들고 Optional로 감싸서 반환, X-> Optional.empty() 반환
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty(); 
    }

    // READ (전체 조회) 기능
    public List<Map<String, Object>> findAll() {  //List<UserDTO> 타입을 반환하는 findAll 메소드 선언
        // 여러 사용자 정보를 담을 ArrayList 객체를 생성합니다.
        List<Map<String, Object>> userList = new ArrayList<>();
        String sql = SqlLoader.getSql("user.select.all");
        try (Connection conn = getConnection();  //try-with-resources 구문 : 자동으로 자원 해제
            // 실행할 SQL문에 파라미터('?')가 없으므로 PreparedStatement 대신 Statement를 사용합니다.
             Statement stmt = conn.createStatement();
             // SELECT 문을 실행하고 결과를 ResultSet 객체(rs)로 받습니다.
             ResultSet rs = stmt.executeQuery(sql)) {
            //다음 행이 없을때까지 while문 반복
            while (rs.next()) { 
                Map<String, Object> user = new HashMap<>(); // HashMap 생성
                user.put("id", rs.getInt("id"));// put으로 데이터 저장
                user.put("name", rs.getString("name"));
                user.put("phone", rs.getString("phone"));
                user.put("email", rs.getString("email"));
                userList.add(user); // List에 Map 객체 추가
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userList;
    }
}