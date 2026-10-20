package com.fortunaball.record.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import com.fortunaball.record.dto.AdminVerificationListResponse;
import com.fortunaball.record.dto.VerificationReviewRequest;
import com.fortunaball.record.dto.VerificationReviewResponse;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** DB 없이 가짜 저장소(Mockito)로 관리자 검수 규칙을 확인한다. */
class AdminVerificationServiceTest {

    private final AnonymousVerificationRepository proofRepo = mock(AnonymousVerificationRepository.class);
    private final NumberIssueRepository issueRepo = mock(NumberIssueRepository.class);
    private final WinningVerificationRepository winningRepo = mock(WinningVerificationRepository.class);
    private AdminVerificationService service;

    @BeforeEach
    void setUp() {
        service = new AdminVerificationService(proofRepo, issueRepo, winningRepo);
    }

    private static BusinessException reject(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    private AnonymousVerification verification(long id, long issueId, ReviewStatus status) {
        AnonymousVerification v = AnonymousVerification.register(issueId, 50L + id, "ABCDEFGHJKLM", "a".repeat(64), "대박");
        ReflectionTestUtils.setField(v, "id", id);
        if (status != ReviewStatus.PENDING) {
            v.review(status, "x", LocalDateTime.of(2026, 10, 20, 9, 0));
        }
        return v;
    }

    private void stubFind(AnonymousVerification v) {
        when(proofRepo.findById(v.getId())).thenReturn(Optional.of(v));
    }

    private static VerificationReviewRequest req(ReviewStatus s, String comment) {
        return new VerificationReviewRequest(s, comment);
    }

    // ---------- 검수 처리 (API-29) ----------

    @Test
    void 대기_건을_승인하면_공개된다() {
        AnonymousVerification v = verification(1, 10, ReviewStatus.PENDING);
        stubFind(v);

        VerificationReviewResponse res = service.review(1L, req(ReviewStatus.APPROVED, null));

        assertEquals("APPROVED", res.reviewStatus());
        assertTrue(res.publicVisible());
        assertTrue(v.isPublicVisible());
        verify(proofRepo).saveAndFlush(v);
        verify(winningRepo).updatePublicByIssue(10L, true);   // 당첨 기록도 함께 공개
    }

    @Test
    void 반려하면_비공개이고_사유가_저장된다() {
        AnonymousVerification v = verification(1, 10, ReviewStatus.PENDING);
        stubFind(v);

        VerificationReviewResponse res = service.review(1L, req(ReviewStatus.REJECTED, "  사진이 흐려요  "));

        assertEquals("REJECTED", res.reviewStatus());
        assertFalse(res.publicVisible());
        assertEquals("사진이 흐려요", v.getAdminComment());
        verify(winningRepo).updatePublicByIssue(10L, false);   // 당첨 기록도 비공개
    }

    @Test
    void 승인된_건을_숨기면_비공개가_되고_다시_승인하면_공개된다() {
        AnonymousVerification v = verification(1, 10, ReviewStatus.APPROVED);
        stubFind(v);

        assertFalse(service.review(1L, req(ReviewStatus.HIDDEN, "개인정보 노출")).publicVisible());
        assertTrue(service.review(1L, req(ReviewStatus.APPROVED, null)).publicVisible());
        verify(winningRepo).updatePublicByIssue(10L, false);
        verify(winningRepo, times(1)).updatePublicByIssue(10L, true);
    }

    @Test
    void 반려와_숨김은_사유가_없으면_거절한다() {
        AnonymousVerification v = verification(1, 10, ReviewStatus.PENDING);
        stubFind(v);

        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.review(1L, req(ReviewStatus.REJECTED, null))).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.review(1L, req(ReviewStatus.REJECTED, "   "))).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.review(1L, req(ReviewStatus.HIDDEN, ""))).getErrorCode());
        verify(proofRepo, never()).saveAndFlush(any());
        verify(winningRepo, never()).updatePublicByIssue(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void 사유가_500자를_넘으면_거절한다() {
        BusinessException e = reject(() -> service.review(1L, req(ReviewStatus.REJECTED, "가".repeat(501))));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 대기로_되돌리는_요청은_거절한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.review(1L, req(ReviewStatus.PENDING, null))).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.review(1L, req(null, null))).getErrorCode());
    }

    @Test
    void 없는_인증이면_NOT_FOUND() {
        when(proofRepo.findById(9L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, reject(() -> service.review(9L, req(ReviewStatus.APPROVED, null))).getErrorCode());
    }

    @Test
    void 허용되지_않는_상태_이동은_CONFLICT() {
        // 반려는 끝, 대기에서 바로 숨김 불가, 같은 상태로 다시 승인 불가
        AnonymousVerification rejected = verification(1, 10, ReviewStatus.REJECTED);
        AnonymousVerification pending = verification(2, 11, ReviewStatus.PENDING);
        AnonymousVerification approved = verification(3, 12, ReviewStatus.APPROVED);
        stubFind(rejected);
        stubFind(pending);
        stubFind(approved);

        assertEquals(ErrorCode.CONFLICT, reject(() -> service.review(1L, req(ReviewStatus.APPROVED, null))).getErrorCode());
        assertEquals(ErrorCode.CONFLICT, reject(() -> service.review(2L, req(ReviewStatus.HIDDEN, "사유"))).getErrorCode());
        assertEquals(ErrorCode.CONFLICT, reject(() -> service.review(3L, req(ReviewStatus.APPROVED, null))).getErrorCode());
        verify(proofRepo, never()).saveAndFlush(any());
    }

    // ---------- 목록 (API-28) ----------

    @Test
    void 목록은_회차와_최고등수를_붙여_준다() {
        AnonymousVerification v1 = verification(2, 10, ReviewStatus.PENDING);
        AnonymousVerification v2 = verification(1, 11, ReviewStatus.PENDING);
        PageRequest pr = PageRequest.of(0, 20);
        when(proofRepo.findByReviewStatus(any(ReviewStatus.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(v1, v2), pr, 2));
        List<Object[]> ranks = new ArrayList<>();
        ranks.add(new Object[] {10L, 4});
        when(proofRepo.findBestRanksByIssueIds(any())).thenReturn(ranks);
        NumberIssue i10 = NumberIssue.create("AAAAAAAAAAAA", "guest-aaaaaaaaaaaaaaaa", 1239, 1, IssueType.STAT_AUTO, null, null);
        NumberIssue i11 = NumberIssue.create("BBBBBBBBBBBB", "guest-aaaaaaaaaaaaaaaa", 1240, 1, IssueType.STAT_AUTO, null, null);
        ReflectionTestUtils.setField(i10, "id", 10L);
        ReflectionTestUtils.setField(i11, "id", 11L);
        when(issueRepo.findAllById(any())).thenReturn(List.of(i10, i11));

        AdminVerificationListResponse res = service.list(ReviewStatus.PENDING, 0, 20);

        assertEquals(2, res.items().size());
        assertEquals(2L, res.items().get(0).id());
        assertEquals(1239, res.items().get(0).roundNo());
        assertEquals(4, res.items().get(0).bestRank());
        assertEquals(1240, res.items().get(1).roundNo());
        assertNull(res.items().get(1).bestRank());          // 검증 결과가 없으면 null
        assertEquals("/api/admin/verifications/2/photo", res.items().get(0).photoUrl());
        assertEquals(2, res.totalElements());
    }

    @Test
    void 상태를_안_주면_전체를_조회한다() {
        when(proofRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        AdminVerificationListResponse res = service.list(null, 0, 20);

        assertTrue(res.items().isEmpty());
        verify(proofRepo).findAll(any(Pageable.class));
        verify(proofRepo, never()).findBestRanksByIssueIds(any());   // 빈 목록이면 추가 조회를 하지 않는다
    }

    @Test
    void 페이지_값이_잘못되면_거절한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(null, -1, 20)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(null, 0, 0)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.list(null, 0, 51)).getErrorCode());
    }
}
