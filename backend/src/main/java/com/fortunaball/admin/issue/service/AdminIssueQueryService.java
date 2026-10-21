package com.fortunaball.admin.issue.service;

import com.fortunaball.admin.common.AdminJdbc;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.issue.dto.AdminIssueItem;
import com.fortunaball.admin.issue.dto.UnverifiedIssueItem;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.issue.domain.VerificationStatus;
import com.fortunaball.issue.repository.IssueNumberRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번호 발급 기록(API-24)과 미검증·검증 실패 발급 건(API-48) 조회. 읽기만 한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - "검증 대기"는 아직 추첨 전인 회차를 뺀다. 추첨이 끝난 회차인데도 검증이 안 된 건만 문제로 보여 준다.
 *    (이번 회차 발급 건이 전부 "문제"로 뜨는 것을 막으려는 것)
 *  - 검증 상태 계산은 사용자 쪽 발급 내역 조회(API-05)와 같은 VerificationStatus.of 를 쓴다.
 */
@Service
public class AdminIssueQueryService {

    static final int GUEST_TOKEN_SHOWN = 6;

    private final JdbcTemplate jdbc;
    private final IssueNumberRepository issueNumberRepository;

    public AdminIssueQueryService(JdbcTemplate jdbc, IssueNumberRepository issueNumberRepository) {
        this.jdbc = jdbc;
        this.issueNumberRepository = issueNumberRepository;
    }

    /** API-24: 회차·발급코드·게스트 식별값으로 조회 (모두 선택) */
    @Transactional(readOnly = true)
    public AdminPage<AdminIssueItem> issues(Integer roundNo, String issueCode, String guestToken,
                                            Integer page, Integer size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("FROM number_issue WHERE 1 = 1");
        if (roundNo != null) {
            where.append(" AND target_round_no = ?");
            args.add(roundNo);
        }
        if (issueCode != null && !issueCode.isBlank()) {
            where.append(" AND issue_code = ?");
            args.add(issueCode.trim());
        }
        if (guestToken != null && !guestToken.isBlank()) {
            where.append(" AND guest_token = ?");
            args.add(guestToken.trim());
        }

        AdminPage<IssueRow> rows = AdminJdbc.page(jdbc,
                "id, issue_code, guest_token, target_round_no, game_count, issue_type, created_at",
                where.toString(), args, "created_at DESC, id DESC", AdminQuery.page(page), AdminQuery.size(size),
                (rs, i) -> new IssueRow(rs.getLong("id"), rs.getString("issue_code"), rs.getString("guest_token"),
                        rs.getInt("target_round_no"), rs.getInt("game_count"), rs.getString("issue_type"),
                        AdminJdbc.time(rs, "created_at")));

        List<Long> ids = rows.items().stream().map(IssueRow::id).toList();
        Map<Long, List<List<Integer>>> games = loadGames(ids);
        Map<Long, VerificationStatus> statuses = loadStatuses(ids);

        List<AdminIssueItem> items = rows.items().stream().map(r -> {
            VerificationStatus status = statuses.getOrDefault(r.id(), VerificationStatus.PENDING);
            return new AdminIssueItem(r.id(), r.issueCode(), maskGuestToken(r.guestToken()), r.targetRoundNo(),
                    r.gameCount(), r.issueType(), status.name(), status.label(), r.createdAt(),
                    games.getOrDefault(r.id(), List.of()));
        }).toList();
        return new AdminPage<>(items, rows.page(), rows.size(), rows.totalCount(), rows.hasNext());
    }

    /** API-48: status 는 PENDING / ERROR (안 보내면 둘 다) */
    @Transactional(readOnly = true)
    public AdminPage<UnverifiedIssueItem> unverified(Integer roundNo, String status, Integer page, Integer size) {
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (wanted != null && !wanted.equals("PENDING") && !wanted.equals("ERROR")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상태는 PENDING 또는 ERROR 만 쓸 수 있어요.",
                    "unverified status=" + status);
        }

        // 발급 1건 = issue_number(게임) 여러 줄. 게임별 검증 결과를 세어서 건 단위로 묶는다.
        StringBuilder inner = new StringBuilder("""
                SELECT n.id AS id, n.issue_code AS issue_code, n.target_round_no AS target_round_no,
                       n.game_count AS game_count, n.created_at AS created_at,
                       COUNT(i.id) AS total_games,
                       COUNT(CASE WHEN v.result_status = 'VERIFIED' THEN 1 END) AS verified_games,
                       COUNT(CASE WHEN v.result_status = 'ERROR' THEN 1 END) AS error_games
                  FROM number_issue n
                  JOIN issue_number i ON i.number_issue_id = n.id
                  LEFT JOIN winning_verification v ON v.issue_number_id = i.id
                 WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (roundNo != null) {
            inner.append(" AND n.target_round_no = ?\n");
            args.add(roundNo);
        }
        inner.append(" GROUP BY n.id, n.issue_code, n.target_round_no, n.game_count, n.created_at\n");

        String drawn = "(SELECT COALESCE(MAX(round_no), 0) FROM draw_round)";
        String pending = "(error_games = 0 AND verified_games < total_games AND n.target_round_no <= " + drawn + ")";
        String error = "(error_games > 0)";
        String having = wanted == null ? "(" + pending + " OR " + error + ")"
                : wanted.equals("ERROR") ? error : pending;
        inner.append(" HAVING ").append(having);

        return AdminJdbc.page(jdbc,
                "t.id, t.issue_code, t.target_round_no, t.game_count, t.created_at, t.verified_games, t.error_games",
                "FROM (" + inner + ") t", args, "t.created_at DESC, t.id DESC",
                AdminQuery.page(page), AdminQuery.size(size),
                (rs, i) -> toUnverified(rs.getLong("id"), rs.getString("issue_code"), rs.getInt("target_round_no"),
                        rs.getInt("game_count"), rs.getLong("verified_games"), rs.getLong("error_games"),
                        AdminJdbc.time(rs, "created_at")));
    }

    // ------------------------------------------------------------------ 보조

    /** 건 하나의 상태와 사유 문구를 정한다. (실패가 하나라도 있으면 ERROR, 아니면 PENDING) */
    static UnverifiedIssueItem toUnverified(Long id, String issueCode, int roundNo, int gameCount,
                                            long verifiedGames, long errorGames, java.time.LocalDateTime createdAt) {
        boolean error = errorGames > 0;
        VerificationStatus status = error ? VerificationStatus.ERROR : VerificationStatus.PENDING;
        String reason = error
                ? "게임 " + errorGames + "개의 검증에 실패했어요. 검증을 다시 실행해 주세요."
                : "추첨은 끝났는데 아직 검증되지 않은 게임이 있어요. (검증 완료 " + verifiedGames + "/" + gameCount + ")";
        return new UnverifiedIssueItem(id, issueCode, roundNo, gameCount, verifiedGames, errorGames,
                status.name(), status.label(), reason, createdAt);
    }

    static String maskGuestToken(String token) {
        if (token == null || token.length() <= GUEST_TOKEN_SHOWN) {
            return token;
        }
        return token.substring(0, GUEST_TOKEN_SHOWN) + "…";
    }

    private Map<Long, List<List<Integer>>> loadGames(Collection<Long> ids) {
        Map<Long, List<List<Integer>>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT number_issue_id, number_1, number_2, number_3, number_4, number_5, number_6"
                        + " FROM issue_number WHERE number_issue_id IN (" + placeholders(ids.size()) + ")"
                        + " ORDER BY number_issue_id, game_order",
                rs -> {
                    List<Integer> numbers = List.of(rs.getInt("number_1"), rs.getInt("number_2"),
                            rs.getInt("number_3"), rs.getInt("number_4"), rs.getInt("number_5"), rs.getInt("number_6"));
                    result.computeIfAbsent(rs.getLong("number_issue_id"), k -> new ArrayList<>()).add(numbers);
                }, ids.toArray());
        return result;
    }

    private Map<Long, VerificationStatus> loadStatuses(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return issueNumberRepository.countVerification(ids).stream().collect(Collectors.toMap(
                IssueNumberRepository.VerificationCount::getIssueId,
                c -> VerificationStatus.of(c.getTotalGames(), c.getVerifiedGames(), c.getErrorGames())));
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private record IssueRow(Long id, String issueCode, String guestToken, int targetRoundNo, int gameCount,
                            String issueType, java.time.LocalDateTime createdAt) {
    }
}
