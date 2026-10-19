package com.fortunaball.verify.repository;

import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.verify.domain.MatchResult;
import com.fortunaball.verify.domain.VerificationResultStatus;
import com.fortunaball.verify.domain.WinningVerification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 MySQL 에 연결해서 확인하는 저장소 테스트. (IssueRepositoryTest 와 같은 방식, 끝나면 자동 롤백)
 * 검증 결과는 draw_round 에 있는 회차만 저장할 수 있어서(FK) 이미 DB 에 있는 1246 회차를 쓴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WinningVerificationRepositoryTest {

    private static final int DRAW_ROUND = 1246;      // DB 에 이미 있는 회차
    private static final int ISSUE_ROUND = 999_999;  // 발급 쪽은 실제 회차와 겹치지 않는 값

    @Autowired
    private WinningVerificationRepository repo;
    @Autowired
    private NumberIssueRepository issueRepo;
    @Autowired
    private IssueNumberRepository numberRepo;

    private Long newGame(String code, int order, String key, List<Integer> numbers) {
        NumberIssue issue = issueRepo.saveAndFlush(NumberIssue.create(
                code, "guest-aaaaaaaaaaaaaaaa", ISSUE_ROUND, 1, IssueType.STAT_AUTO, null, null));
        return numberRepo.saveAndFlush(IssueNumber.of(issue.getId(), order, key, numbers)).getId();
    }

    @Test
    void 낙첨_결과가_저장되고_다시_읽힌다_등수는_null() {
        Long game = newGame("VERIFYCODE01", 1, "03-08-18-27-33-41", List.of(3, 8, 18, 27, 33, 41));

        repo.insertIfAbsent(game, DRAW_ROUND, 1, false, null, "VERIFIED");

        WinningVerification saved = repo.findByIssueNumberIdIn(List.of(game)).get(0);
        assertEquals(DRAW_ROUND, saved.getRoundNo());
        assertEquals(1, saved.getMatchCount());
        assertFalse(saved.isBonusMatched());
        assertNull(saved.getWinningRank());
        assertEquals(VerificationResultStatus.VERIFIED, saved.getResultStatus());
        assertNull(saved.toMatchResult().rank());
    }

    @Test
    void 등수가_있는_결과도_저장되고_계산_결과로_바뀐다() {
        Long game = newGame("VERIFYCODE02", 1, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6));

        repo.insertIfAbsent(game, DRAW_ROUND, 5, true, 2, "VERIFIED");

        MatchResult r = repo.findByIssueNumberIdIn(List.of(game)).get(0).toMatchResult();
        assertEquals(5, r.matchCount());
        assertTrue(r.bonusMatched());
        assertEquals(2, r.rank());
    }

    @Test
    void 같은_게임을_두번_저장해도_한건만_남고_먼저_저장된_값이_유지된다() {
        Long game = newGame("VERIFYCODE03", 1, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6));

        repo.insertIfAbsent(game, DRAW_ROUND, 3, false, 5, "VERIFIED");
        repo.insertIfAbsent(game, DRAW_ROUND, 6, false, 1, "VERIFIED");   // 이미 있으니 건너뜀

        List<WinningVerification> all = repo.findByIssueNumberIdIn(List.of(game));
        assertEquals(1, all.size());
        assertEquals(5, all.get(0).getWinningRank());
    }
}
