package com.fortunaball.admin.round.service;

import com.fortunaball.admin.round.dto.AdminDrawItem;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.stat.domain.SnapshotStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 데이터·통계 상태 목록 (API-46). 읽기만 한다. 누락 회차를 찾는 것이 목적이다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 범위를 안 보내면 "수집된 최신 회차 기준 최근 20회"를 보여 준다. 최대 200회까지 한 번에 볼 수 있다.
 *  - 범위 안에 DB 에 없는 회차가 있으면 missing=true 로 한 줄을 만들어 보여 준다. (누락 확인용)
 *  - 최신 회차보다 큰 회차를 요청하면 그 회차들도 missing 으로 나온다. (아직 안 열린 회차일 수 있다)
 */
@Service
public class AdminDrawQueryService {

    static final int DEFAULT_COUNT = 20;
    static final int MAX_COUNT = 200;
    static final int PRIZE_RANKS = 5;

    private final DrawRoundRepository drawRoundRepository;
    private final JdbcTemplate jdbc;

    public AdminDrawQueryService(DrawRoundRepository drawRoundRepository, JdbcTemplate jdbc) {
        this.drawRoundRepository = drawRoundRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AdminDrawItem> list(Integer fromRound, Integer toRound) {
        int latest = drawRoundRepository.findTopByOrderByRoundNoDesc().map(DrawRound::getRoundNo).orElse(0);
        int to = toRound != null ? toRound : latest;
        int from = fromRound != null ? fromRound : Math.max(1, to - DEFAULT_COUNT + 1);
        if (from < 1 || to < from || to - from + 1 > MAX_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "회차 범위는 1 이상, 한 번에 최대 " + MAX_COUNT + "회까지 볼 수 있어요.",
                    "회차 범위 오류: from=" + from + ", to=" + to);
        }

        Map<Integer, DrawRound> rounds = new HashMap<>();
        for (DrawRound r : drawRoundRepository.findByRoundNoBetweenOrderByRoundNoAsc(from, to)) {
            rounds.put(r.getRoundNo(), r);
        }
        Map<Integer, Integer> prizeCounts = new HashMap<>();
        jdbc.query("SELECT round_no, COUNT(DISTINCT winning_rank) AS cnt FROM draw_prize"
                        + " WHERE round_no BETWEEN ? AND ? GROUP BY round_no",
                rs -> {
                    prizeCounts.put(rs.getInt("round_no"), rs.getInt("cnt"));
                }, from, to);
        Map<Integer, SnapshotStatus> stats = new HashMap<>();
        jdbc.query("SELECT target_round_no, status FROM stat_round_snapshot WHERE target_round_no BETWEEN ? AND ?",
                rs -> {
                    stats.put(rs.getInt("target_round_no"), SnapshotStatus.valueOf(rs.getString("status")));
                }, from, to);

        return build(from, to, rounds, prizeCounts, stats);
    }

    /** 범위 안 회차마다 한 줄을 만든다. (DB 조회와 분리해서 테스트하기 쉽게 했다) 최신 회차가 위로 온다. */
    static List<AdminDrawItem> build(int from, int to, Map<Integer, DrawRound> rounds,
                                     Map<Integer, Integer> prizeCounts, Map<Integer, SnapshotStatus> stats) {
        List<AdminDrawItem> items = new ArrayList<>();
        for (int no = to; no >= from; no--) {
            DrawRound round = rounds.get(no);
            SnapshotStatus stat = stats.get(no);
            int ranks = prizeCounts.getOrDefault(no, 0);
            items.add(new AdminDrawItem(no,
                    round == null ? null : round.getDrawDate(),
                    round == null,
                    round == null ? null : (ranks >= PRIZE_RANKS ? "COMPLETE" : "INCOMPLETE"),
                    ranks,
                    stat == null ? null : stat.name(),
                    stat == null ? null : stat.label()));
        }
        return items;
    }
}
