package com.example.demo.dao;

import com.example.demo.entity.LoginAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository // DAO 클래스임을 나타내는 어노테이션 (db와 통신하는 저장소)
public class LoginAccountDAO { //LoginAccountDAO 클래스 선언

    @Autowired // DataSource 객체를 주입받아 데이터베이스 연결에 사용
    private DataSource dataSource; // DataSource: 커넥션 풀에서 Connection 객체를 얻기 위한 인터페이스

    private Connection getConnection() throws SQLException { // 데이터베이스 연결을 얻는 메서드
        return dataSource.getConnection(); // DataSource에서 Connection 객체를 얻어 반환
    } 

    /**
     * login_accounts 테이블에 새로운 계정 정보를 저장(INSERT)합니다.
     * @param account 저장할 LoginAccount 객체
     * @return 성공 시 1, 실패 시 0
     */
    public int save(LoginAccount account) {
        String sql = SqlLoader.getSql("login_account.insert");
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, account.getUserId());
            pstmt.setString(2, account.getEmail());
            pstmt.setString(3, account.getPassword());
            pstmt.setString(4, account.getRole());

            return pstmt.executeUpdate(); // 쿼리 실행 및 영향받은 행의 수 반환

        } catch (SQLException e) {
            e.printStackTrace();
            return 0; // 예외 발생 시 0 반환
        }
    }

    /**
     * 이메일 주소로 login_accounts 테이블에서 계정 정보를 조회합니다.
     * @param email 조회할 이메일
     * @return 조회된 계정 정보를 담은 Optional 객체
     */
    public Optional<LoginAccount> findByEmail(String email) { // 이메일로 계정 조회 메서드
        String sql = SqlLoader.getSql("login_account.select.by_email"); 
        try (Connection conn = getConnection(); // 데이터베이스 연결 얻기
             PreparedStatement pstmt = conn.prepareStatement(sql)) { // PreparedStatement 생성

            pstmt.setString(1, email); // SQL 쿼리의 첫 번째 파라미터에 이메일 설정

            try (ResultSet rs = pstmt.executeQuery()) { //executeQuery()로 쿼리 실행 후 ResultSet 얻기
                if (rs.next()) { // 조회된 데이터가 있으면
                    LoginAccount account = new LoginAccount(); // LoginAccount 객체 생성
                    account.setId(rs.getLong("id"));
                    account.setUserId(rs.getLong("user_id"));
                    account.setEmail(rs.getString("email"));
                    account.setPassword(rs.getString("password"));
                    account.setRole(rs.getString("role"));
                    return Optional.of(account); // 조회된 데이터가 있으면 Optional로 감싸서 반환
                }
            }
        } catch (SQLException e) {
            e.printStackTrace(); // 예외 발생 시 스택 트레이스 출력
        }
        return Optional.empty(); // 데이터가 없거나 예외 발생 시 빈 Optional 반환
    }
}