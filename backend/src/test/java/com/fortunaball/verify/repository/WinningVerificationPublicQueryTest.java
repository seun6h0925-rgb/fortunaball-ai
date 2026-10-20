package com.fortunaball.verify.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.verify.domain.WinningVerification;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 실제 MySQL 로 확인하는 "공개 설정" 쿼리 테스트. (끝나면 자동 롤백)
 * ⚠ winning_verification 의 회차는 draw_round 를 참조하므로 DB 에 있는 가장 최근 회차를 쓴다. (없으면 건너뜀)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WinningVerificationPublicQueryTest {

    @Autowired
    private WinningVerificationRepository winningRepo;
    @Autowired
    private IssueNumberRepository numberRepo;
    @Autowired
    private NumberIssueRepository issueRepo;
    @Autowired
    private DrawRoundRepository drawRepo;

    private Long find(Long issueNumberId) {
        return winningRepo.findByIssueNumberIdIn(List.of(issueNumberId)).get(0).getId();
    }

    @Test
    void 발급건_단위로_당첨_조합만_공개되고_목록에_나온다() {
        DrawRound round = drawRepo.findTopByOrderByRoundNoDesc().orElse(null);
        Assumptions.assumeTrue(round != null, "draw_round 데이터가 없으면 건너뜀");
        Long issueId = issueRepo.saveAndFlush(NumberIssue.create(
                "PUBQUERY0001", "guest-aaaaaaaaaaaaaaaa", 999_999, 2, IssueType.STAT_AUTO, null, null)).getId();
        IssueNumber win = numberRepo.saveAndFlush(IssueNumber.of(issueId, 1, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6)));
        IssueNumber lose = numberRepo.saveAndFlush(IssueNumber.of(issueId, 2, "07-08-09-10-11-12", List.of(7, 8, 9, 10, 11, 12)));
        winningRepo.insertIfAbsent(win.getId(), round.getRoundNo(), 4, false, 4, "VERIFIED");
        winningRepo.insertIfAbsent(lose.getId(), round.getRoundNo(), 1, false, null, "VERIFIED");
        Long winRecord = find(win.getId());
        Long loseRecord = find(lose.getId());
        PageRequest page = PageRequest.of(0, 1000, Sort.by(Sort.Order.desc("roundNo"), Sort.Order.asc("winningRank"), Sort.Order.desc("id")));

        // 처음에는 비공개라 목록에 없다
        assertFalse(winningRepo.findPublicRecords(null, null, page).getContent().stream().anyMatch(w -> w.getId().equals(winRecord)));

        int changed = winningRepo.updatePublicByIssue(issueId, true);

        assertEquals(1, changed);                                   // 낙첨 조합은 바뀌지 않는다
        List<WinningVerification> listed = winningRepo.findPublicRecords(null, null, page).getContent();
        assertTrue(listed.stream().anyMatch(w -> w.getId().equals(winRecord)));
        assertFalse(listed.stream().anyMatch(w -> w.getId().equals(loseRecord)));
        // 등수 조건
        assertTrue(winningRepo.findPublicRecords(round.getRoundNo(), 4, page).getContent().stream().anyMatch(w -> w.getId().equals(winRecord)));
        assertFalse(winningRepo.findPublicRecords(round.getRoundNo(), 3, page).getContent().stream().anyMatch(w -> w.getId().equals(winRecord)));

        // 한 건 단위 변경: 당첨 건은 바뀌고, 낙첨 건은 바뀌지 않는다
        assertEquals(1, winningRepo.updatePublicById(winRecord, false));
        assertEquals(0, winningRepo.updatePublicById(loseRecord, true));
        assertFalse(winningRepo.findPublicRecords(null, null, page).getContent().stream().anyMatch(w -> w.getId().equals(winRecord)));
    }
}
