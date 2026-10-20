package com.fortunaball.record.repository;

import com.fortunaball.common.file.FileUpload;
import com.fortunaball.common.file.FileUploadRepository;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 MySQL 에 연결해서 확인하는 저장소 테스트. (끝나면 자동 롤백, DB 에 남지 않는다)
 * 핵심: "발급코드당 인증 1건 / 같은 용지 1건" 을 DB 가 지키는지, 반려되면 다시 등록되는지.
 *
 * ⚠ 이 테스트는 docs/db/schema.sql 의 [추가 2026-10-20] 컬럼(ticket_hash 등)이 DB 에 있어야 통과한다.
 *
 * ⚠ 제약 위반(예외) 이후에는 같은 테스트 안에서 DB 를 더 쓰지 않는다. 그래서 위반 확인은 테스트마다 마지막에 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnonymousVerificationRepositoryTest {

    private static final int ISSUE_ROUND = 999_999;   // 실제 회차와 겹치지 않는 값

    @Autowired
    private AnonymousVerificationRepository repo;
    @Autowired
    private NumberIssueRepository issueRepo;
    @Autowired
    private FileUploadRepository fileRepo;

    private Long newIssue(String code) {
        return issueRepo.saveAndFlush(NumberIssue.create(
                code, "guest-aaaaaaaaaaaaaaaa", ISSUE_ROUND, 1, IssueType.STAT_AUTO, null, null)).getId();
    }

    private Long newFile() {
        String stored = UUID.randomUUID().toString().replace("-", "") + ".jpg";
        return fileRepo.saveAndFlush(FileUpload.uploaded("a.jpg", stored, "C:/test/" + stored, 10, "jpg", "image/jpeg")).getId();
    }

    /** 가짜 용지 해시(64자). 같은 글자를 반복해서 만든다. */
    private static String hash(char c) {
        return String.valueOf(c).repeat(64);
    }

    private AnonymousVerification register(Long issueId, String code, String ticketHash) {
        return AnonymousVerification.register(issueId, newFile(), code, ticketHash, "축하해 주세요");
    }

    @Test
    void 등록하면_검수대기_비공개로_저장된다() {
        Long issue = newIssue("PROOFCODE001");

        AnonymousVerification saved = repo.saveAndFlush(register(issue, "PROOFCODE001", hash('a')));

        assertNotNull(saved.getId());
        AnonymousVerification found = repo.findById(saved.getId()).orElseThrow();
        assertEquals(ReviewStatus.PENDING, found.getReviewStatus());
        assertFalse(found.isPublicVisible());
        assertEquals("축하해 주세요", found.getComment());
        assertEquals(hash('a'), found.getTicketHash());
    }

    @Test
    void 반려가_아닌_인증이_있으면_존재_확인이_참() {
        Long issue = newIssue("PROOFCODE002");
        repo.saveAndFlush(register(issue, "PROOFCODE002", hash('b')));

        assertTrue(repo.existsByNumberIssueIdAndReviewStatusNot(issue, ReviewStatus.REJECTED));
        assertTrue(repo.existsByTicketHashAndReviewStatusNot(hash('b'), ReviewStatus.REJECTED));
        assertFalse(repo.existsByTicketHashAndReviewStatusNot(hash('c'), ReviewStatus.REJECTED));
    }

    @Test
    void 반려되면_존재_확인이_거짓이_되고_같은_코드와_용지로_다시_등록할_수_있다() {
        Long issue = newIssue("PROOFCODE003");
        AnonymousVerification first = repo.saveAndFlush(register(issue, "PROOFCODE003", hash('d')));

        first.review(ReviewStatus.REJECTED, "사진이 흐려요", LocalDateTime.now());
        repo.saveAndFlush(first);

        assertFalse(repo.existsByNumberIssueIdAndReviewStatusNot(issue, ReviewStatus.REJECTED));
        assertFalse(repo.existsByTicketHashAndReviewStatusNot(hash('d'), ReviewStatus.REJECTED));

        // 같은 발급코드·같은 용지로 다시 등록 → 막히지 않는다
        AnonymousVerification second = repo.saveAndFlush(register(issue, "PROOFCODE003", hash('d')));
        assertNotNull(second.getId());
    }

    @Test
    void 승인하면_검수정보가_기록된다() {
        Long issue = newIssue("PROOFCODE004");
        AnonymousVerification v = repo.saveAndFlush(register(issue, "PROOFCODE004", hash('e')));

        v.review(ReviewStatus.APPROVED, "확인했어요", LocalDateTime.of(2026, 10, 20, 12, 0));
        repo.saveAndFlush(v);

        AnonymousVerification found = repo.findById(v.getId()).orElseThrow();
        assertEquals(ReviewStatus.APPROVED, found.getReviewStatus());
        assertEquals("확인했어요", found.getAdminComment());
        assertNotNull(found.getReviewedAt());
    }

    @Test
    void 같은_발급코드로_반려_아닌_인증을_또_넣으면_DB가_거절한다() {
        Long issue = newIssue("PROOFCODE005");
        repo.saveAndFlush(register(issue, "PROOFCODE005", hash('f')));

        assertThrows(DataIntegrityViolationException.class,
                () -> repo.saveAndFlush(register(issue, "PROOFCODE005", hash('g'))));
    }

    @Test
    void 같은_용지를_다른_발급코드로_또_넣으면_DB가_거절한다() {
        Long issueA = newIssue("PROOFCODE006");
        Long issueB = newIssue("PROOFCODE007");
        repo.saveAndFlush(register(issueA, "PROOFCODE006", hash('h')));

        assertThrows(DataIntegrityViolationException.class,
                () -> repo.saveAndFlush(register(issueB, "PROOFCODE007", hash('h'))));
    }
}
