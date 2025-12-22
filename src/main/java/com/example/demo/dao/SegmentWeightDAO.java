// 추가됨: 2번 문제(신성동→유성) 최단경로 계산을 위해 segment_weight(BUS) 구간을 조회하는 DAO

package com.example.demo.dao;

import java.sql.Connection; // DB 커넥션 객체
import java.sql.PreparedStatement; // SQL 실행 준비 객체
import java.sql.ResultSet; // SELECT 결과를 담는 객체
import java.sql.SQLException; // SQL 예외
import java.util.ArrayList; // List 구현체
import java.util.HashMap; // Map 구현체
import java.util.List; // 결과 목록 타입
import java.util.Map; // row 1개를 담는 타입

import javax.sql.DataSource; // 커넥션 풀(DataSource)

import org.springframework.beans.factory.annotation.Autowired; // 스프링 DI
import org.springframework.stereotype.Repository; // DAO 컴포넌트 등록

@Repository // DAO(Repository)로 스프링 빈 등록
public class SegmentWeightDAO {

    @Autowired // DataSource 자동 주입
    private DataSource dataSource; // DB 커넥션 풀

    // DB 커넥션 획득
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection(); // 커넥션 반환
    }

    // sql.properties에서 SQL을 가져오는 공통 메서드(다른 DAO와 동일 패턴)
    private String sql(String key) {
        return SqlLoader.getSql(key); // key로 SQL 문자열 조회
    }

    /**
     * segment_weight 테이블에서 BUS 모드 구간(정류장↔정류장)을 전부 조회한다.
     *
     * - DTO를 쓰지 않으므로, row는 Map<String, Object>로 반환한다.
     * - 최단경로 계산 시:
     *   from_id -> to_id 를 간선으로 보고 travel_sec_avg를 가중치(시간)로 사용한다.
     *
     * @return BUS 구간 전체 목록(각 행은 Map 형태)
     */
    public List<Map<String, Object>> findAllBusSegments() {

        List<Map<String, Object>> list = new ArrayList<>(); // 반환할 결과 목록

        // sql.properties에 추가할 키(아래에 내가 같이 줄 것)
        String query = sql("segment_weight.select.all_bus"); // BUS 구간 전체 조회 SQL

        try (
            Connection conn = getConnection(); // 커넥션 획득
            PreparedStatement ps = conn.prepareStatement(query) // SQL 준비
        ) {

            try (ResultSet rs = ps.executeQuery()) { // SQL 실행 후 결과셋 획득

                while (rs.next()) { // 한 행(row)씩 순회

                    Map<String, Object> row = new HashMap<>(); // row 1개를 Map에 담는다

                    // ---- 기본 식별 컬럼 ----
                    row.put("mode", rs.getString("mode")); // 'BUS'
                    row.put("route_id", rs.getString("route_id")); // 버스 노선 ID
                    row.put("from_id", rs.getString("from_id")); // 출발 정류장 ID
                    row.put("to_id", rs.getString("to_id")); // 도착 정류장 ID

                    // ---- 좌표(정류장) ----
                    row.put("from_lat", rs.getDouble("from_lat")); // 출발 위도
                    row.put("from_lng", rs.getDouble("from_lng")); // 출발 경도
                    row.put("to_lat", rs.getDouble("to_lat")); // 도착 위도
                    row.put("to_lng", rs.getDouble("to_lng")); // 도착 경도

                    // ---- 구간 정보 ----
                    row.put("distance_m", rs.getDouble("distance_m")); // 구간 거리(m)
                    row.put("travel_sec_avg", rs.getDouble("travel_sec_avg")); // 평균 소요시간(초)
                    row.put("sample_count", rs.getInt("sample_count")); // 샘플 수(누적)

                    list.add(row); // 결과 목록에 추가
                }
            }

        } catch (SQLException e) {
            // 기존 DAO들과 동일하게 우선은 스택트레이스 출력(디버깅용)
            e.printStackTrace();
        }

        return list; // 조회 결과 반환
    }
}

// 추가됨 끝
