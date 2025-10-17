package com.example.demo.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//@Repository: 이 클래스가 데이터베이스와 통신하는 부품(DAO)임을 Spring
@Repository
public class RoleDAO {

    @Autowired //@Autowired: Spring이 미리 설정해 둔 데이터베이스 연결
    private DataSource dataSource; //'dataSource' 변수에 자동으로 연결
    //데이터베이스와 실제 연결을 생성
    private Connection getConnection() throws SQLException {
        //dataSource 객체로부터 현재 사용 가능한 DB 연결 통로(Connection)를 하나 빌려와서 반환
        return dataSource.getConnection();
    }

    /**
     * roles 테이블의 모든 역할 목록을 조회합니다.
     * @return 역할 목록 (List of Maps)
     */
    public List<Map<String, Object>> findAll() {
        //조회 결과를 담을 비어있는 리스트
        List<Map<String, Object>> roleList = new ArrayList<>();
        //roles 테이블의 모든 컬럼(*)과 모든 행을 가져오는 SQL 명령어 준비
        String sql = "SELECT * FROM roles";

        //try-with-resources: 이 블록이 끝나면 conn, pstmt, rs 같은 자원들이 자동반환
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            //rs.next(): 결과(ResultSet)에서 다음 행이 있는지 확인
            while (rs.next()) {
                //각 행의 데이터를 담을 맵 생성
                Map<String, Object> role = new HashMap<>();
                //role_id는 VARCHAR(문자열)이므로 getString으로 읽어야 합니다.
                role.put("role_id", rs.getString("role_id"));
                //role_name도 VARCHAR(문자열)이므로 getString으로 읽어야 합니다.
                role.put("role_name", rs.getString("role_name"));
                roleList.add(role);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleList;
    }
}