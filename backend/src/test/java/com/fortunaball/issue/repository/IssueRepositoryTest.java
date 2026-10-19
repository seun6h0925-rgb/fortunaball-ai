package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 MySQL(.env 의 DB) 에 연결해서 확인하는 저장소 테스트.
 * 📚 배운 내용: @DataJpaTest (JPA 부분만 띄우는 테스트). 각 테스트가 끝나면 자동으로 되돌려서(롤백) DB 에 데이터가 남지 않는다.
 *
 * ⚠ MySQL 전용 문법(ON DUPLICATE KEY)을 쓰므로 가짜 DB(H2)로 바꾸지 않고 실제 DB 를 쓴다. (replace = NONE)
 *   DB 가 꺼져 있거나 .env 가 없으면 이 테스트는 실패한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IssueRepositoryTest {

    private static final int ROUND = 999_999;                 // 실제 회차와 겹치지 않는 값
    private static final String KEY = "03-08-18-27-33-41";

    @Autowired
    private ComboIssueCountRepository countRepo;
    @Autowired
    private NumberIssueRepository issueRepo;
    @Autowired
    private IssueNumberRepository numberRepo;

    @Test
    void 횟수_행은_한번만_만들어지고_3번까지만_올라간다() {
        countRepo.ensureRow(ROUND, KEY);
        countRepo.ensureRow(ROUND, KEY);                      // 두 번 불러도 행은 하나
        assertEquals(0, countRepo.findCount(ROUND, KEY).orElseThrow());

        assertEquals(1, countRepo.incrementIfBelow(ROUND, KEY, 3));
        assertEquals(1, countRepo.incrementIfBelow(ROUND, KEY, 3));
        assertEquals(1, countRepo.incrementIfBelow(ROUND, KEY, 3));
        assertEquals(0, countRepo.incrementIfBelow(ROUND, KEY, 3));   // 4번째는 거절
        assertEquals(3, countRepo.findCount(ROUND, KEY).orElseThrow());
    }

    @Test
    void 횟수가_찬_조합만_제외_목록에_나온다() {
        countRepo.ensureRow(ROUND, KEY);
        countRepo.incrementIfBelow(ROUND, KEY, 3);
        countRepo.incrementIfBelow(ROUND, KEY, 3);
        assertTrue(countRepo.findKeysAtLimit(ROUND, 3).isEmpty());    // 2번 → 아직 아님

        countRepo.incrementIfBelow(ROUND, KEY, 3);
        assertEquals(List.of(KEY), countRepo.findKeysAtLimit(ROUND, 3));
        assertTrue(countRepo.findKeysAtLimit(ROUND + 1, 3).isEmpty()); // 다른 회차는 따로 센다
    }

    @Test
    void 행이_없는_조합은_올릴_수_없다() {
        assertEquals(0, countRepo.incrementIfBelow(ROUND, "01-02-03-04-05-06", 3));
    }

    @Test
    void 발급_기록과_번호가_저장되고_다시_읽힌다() {
        NumberIssue issue = issueRepo.saveAndFlush(NumberIssue.create(
                "TESTCODE12345", "guest-aaaaaaaaaaaaaaaa", ROUND, 2,
                IssueType.STAT_CUSTOM, "STAT_PATTERN", "HOT_NUMBER"));
        numberRepo.saveAndFlush(IssueNumber.of(issue.getId(), 1, KEY, List.of(3, 8, 18, 27, 33, 41)));
        numberRepo.saveAndFlush(IssueNumber.of(issue.getId(), 2, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6)));

        assertTrue(issueRepo.existsByIssueCode("TESTCODE12345"));
        List<IssueNumber> saved = numberRepo.findByNumberIssueIdOrderByGameOrderAsc(issue.getId());
        assertEquals(2, saved.size());
        assertEquals(List.of(3, 8, 18, 27, 33, 41), saved.get(0).numbers());
        assertEquals(KEY, saved.get(0).getComboKey());
    }
}
