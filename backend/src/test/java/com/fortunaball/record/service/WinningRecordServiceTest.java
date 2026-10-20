package com.fortunaball.record.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import com.fortunaball.record.dto.WinningRecordListResponse;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import com.fortunaball.verify.domain.WinningVerification;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** DB 없이 가짜로 당첨 기록 목록(API-17) 조립을 확인한다. */
class WinningRecordServiceTest {

    private final WinningVerificationRepository winningRepo = mock(WinningVerificationRepository.class);
    private final IssueNumberRepository numberRepo = mock(IssueNumberRepository.class);
    private final AnonymousVerificationRepository proofRepo = mock(AnonymousVerificationRepository.class);
    private WinningRecordService service;

    @BeforeEach
    void setUp() {
        service = new WinningRecordService(winningRepo, numberRepo, proofRepo);
    }

    private static BusinessException reject(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    private static WinningVerification record(long id, long issueNumberId, int round, int rank, int match) {
        WinningVerification w = mock(WinningVerification.class);
        when(w.getId()).thenReturn(id);
        when(w.getIssueNumberId()).thenReturn(issueNumberId);
        when(w.getRoundNo()).thenReturn(round);
        when(w.getWinningRank()).thenReturn(rank);
        when(w.getMatchCount()).thenReturn(match);
        when(w.isBonusMatched()).thenReturn(false);
        when(w.getVerifiedAt()).thenReturn(LocalDateTime.of(2026, 10, 20, 10, 52));
        return w;
    }

    private static IssueNumber issueNumber(long id, long issueId) {
        IssueNumber n = IssueNumber.of(issueId, 1, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6));
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Test
    void 공개된_당첨_기록에_사용자_한마디를_붙여_준다() {
        WinningVerification w1 = record(1, 101, 1239, 4, 4);
        WinningVerification w2 = record(2, 102, 1238, 5, 3);
        when(winningRepo.findPublicRecords(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(w1, w2), PageRequest.of(0, 20), 2));
        when(numberRepo.findAllById(any())).thenReturn(List.of(issueNumber(101, 10), issueNumber(102, 11)));
        AnonymousVerification approved = AnonymousVerification.register(10L, 55L, "ABCDEFGHJKLM", "a".repeat(64), "대박");
        when(proofRepo.findByNumberIssueIdInAndReviewStatusAndPublicVisibleTrue(any(), eq(ReviewStatus.APPROVED)))
                .thenReturn(List.of(approved));

        WinningRecordListResponse res = service.list(null, null, 0, 20);

        assertEquals(2, res.items().size());
        assertEquals(1239, res.items().get(0).roundNo());
        assertEquals(4, res.items().get(0).rank());
        assertEquals("대박", res.items().get(0).comment());
        assertNull(res.items().get(1).comment());       // 한마디가 없는 건은 null
        assertEquals(2, res.totalElements());
    }

    @Test
    void 결과가_없으면_추가_조회를_하지_않는다() {
        when(winningRepo.findPublicRecords(eq(1239), eq(1), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        WinningRecordListResponse res = service.list(1239, 1, 0, 20);

        assertTrue(res.items().isEmpty());
        verify(numberRepo, never()).findAllById(any());
        verify(proofRepo, never()).findByNumberIssueIdInAndReviewStatusAndPublicVisibleTrue(any(), any());
    }

    @Test
    void 잘못된_조건은_거절한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(null, null, -1, 20)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(null, null, 0, 51)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(0, null, 0, 20)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(null, 6, 0, 20)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(null, 0, 0, 20)).getErrorCode());
    }
}
