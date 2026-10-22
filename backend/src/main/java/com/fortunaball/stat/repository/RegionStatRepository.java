package com.fortunaball.stat.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 지역별 당첨 통계용 저장소 (store_winning_history + lotto_store). 읽기 전용이라 JdbcTemplate 으로 직접 묶는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 폐점한 판매점도 센다. 폐점 여부와 상관없이 "그 지역에서 실제로 당첨이 나온 기록"이기 때문이다.
 *    (지도·검색에서 폐점을 빼는 것과 다르다. 이 통계는 지역 단위 과거 사실이다)
 *  - "인터넷 복권판매사이트"는 실제 가게가 아니라서 뺀다 (판매점 조회와 같은 기준).
 *  - 시도가 비어 있는 판매점은 지역을 알 수 없어 뺀다.
 */
@Repository
public class RegionStatRepository {

    /** 시도별 합계 한 줄 */
    public record RegionRow(String sido, int storeCount, int firstCount, int secondCount) { }

    private final JdbcTemplate jdbc;

    public RegionStatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 회차 범위(양 끝 포함) 안의 시도별 판매점 수·1등 건수·2등 건수 */
    public List<RegionRow> sumByRegion(int fromRound, int toRound) {
        return jdbc.query(
                "SELECT s.sido, COUNT(DISTINCT s.id), SUM(h.winning_rank = 1), SUM(h.winning_rank = 2) "
                        + "FROM store_winning_history h JOIN lotto_store s ON s.id = h.lotto_store_id "
                        + "WHERE h.round_no BETWEEN ? AND ? "
                        + "AND s.sido IS NOT NULL AND s.sido <> '' AND s.store_name <> '인터넷 복권판매사이트' "
                        + "GROUP BY s.sido",
                (rs, i) -> new RegionRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)),
                fromRound, toRound);
    }

    /** 당첨 이력이 있는 가장 앞 회차 (예: 262). 이력이 없으면 null */
    public Integer firstHistoryRound() {
        return jdbc.query("SELECT MIN(round_no) FROM store_winning_history", rs -> {
            if (!rs.next()) {
                return null;
            }
            int v = rs.getInt(1);
            return rs.wasNull() ? null : v;
        });
    }
}
