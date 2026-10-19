package com.fortunaball.verify.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.verify.domain.MatchResult;
import com.fortunaball.verify.domain.WinningVerification;
import com.fortunaball.verify.dto.IssueResultResponse;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** DB 없이 가짜 저장소(Mockito)로 당첨 검증 조회 흐름을 확인한다. (저장 SQL 자체는 WinningVerificationRepositoryTest 가 실제 DB 로 확인) */
class IssueResultServiceTest {

    private static final String CODE = "ABCDEFGHJKMN";
    private static final int ROUND = 1247;

    private final NumberIssueRepository issueRepo = mock(NumberIssueRepository.class);
    private final IssueNumberRepository numberRepo = mock(IssueNumberRepository.class);
    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final WinningVerificationRepository verificationRepo = mock(WinningVerificationRepository.class);
    private final RequestRateLimiter limiter = mock(RequestRateLimiter.class);
    private final RateLimitProperties rateProps = new RateLimitProperties(30, 20);
    private IssueResultService service;

    @BeforeEach
    void setUp() {
        service = new IssueResultService(issueRepo, numberRepo, drawRepo, verificationRepo, limiter, rateProps);
    }

    /** 게임 2줄짜리 발급 건: 1게임 = 1~6, 2게임 = 7~12 (발급 번호 id 는 101, 102) */
    private void stubIssue() {
        NumberIssue issue = NumberIssue.create(CODE, "guest-aaaaaaaaaaaaaaaa", ROUND, 2, IssueType.STAT_AUTO, null, null);
        ReflectionTestUtils.setField(issue, "id", 10L);
        IssueNumber g1 = IssueNumber.of(10L, 1, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6));
        IssueNumber g2 = IssueNumber.of(10L, 2, "07-08-09-10-11-12", List.of(7, 8, 9, 10, 11, 12));
        ReflectionTestUtils.setField(g1, "id", 101L);
        ReflectionTestUtils.setField(g2, "id", 102L);
        when(issueRepo.findByIssueCode(CODE)).thenReturn(Optional.of(issue));
        when(numberRepo.findByNumberIssueIdOrderByGameOrderAsc(10L)).thenReturn(List.of(g1, g2));
    }

    /** 당첨번호 1~6, 보너스 7 */
    private void stubDraw() {
        when(drawRepo.findByRoundNo(ROUND)).thenReturn(Optional.of(
                new DrawRound(ROUND, LocalDate.of(2026, 10, 24), List.of(1, 2, 3, 4, 5, 6), 7, "TEST")));
    }

    @Test
    void 추첨_전이면_검증_대기이고_아무것도_저장하지_않는다() {
        stubIssue();
        when(drawRepo.findByRoundNo(ROUND)).thenReturn(Optional.empty());

        IssueResultResponse res = service.getResult(CODE, "1.1.1.1");

        assertFalse(res.drawn());
        assertEquals("PENDING", res.verificationStatus());
        assertEquals("검증 대기", res.verificationLabel());
        assertNull(res.winningNumbers());
        assertNull(res.bestRank());
        assertEquals(2, res.games().size());
        assertEquals(List.of(7, 8, 9, 10, 11, 12), res.games().get(1).numbers());
        assertNull(res.games().get(0).rank());
        verify(verificationRepo, never()).insertIfAbsent(anyLong(), anyInt(), anyInt(), anyBoolean(), any(), anyString());
        verify(limiter).check("issue-result", "1.1.1.1", 20);
    }

    @Test
    void 추첨_후_저장된_결과가_없으면_계산해서_저장하고_돌려준다() {
        stubIssue();
        stubDraw();
        when(verificationRepo.findByIssueNumberIdIn(any())).thenReturn(List.of());

        IssueResultResponse res = service.getResult(CODE, "1.1.1.1");

        assertTrue(res.drawn());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), res.winningNumbers());
        assertEquals(7, res.bonusNumber());
        assertEquals("VERIFIED", res.verificationStatus());
        assertEquals(1, res.bestRank());
        assertEquals("1등", res.bestRankLabel());

        IssueResultResponse.Game first = res.games().get(0);
        assertEquals(6, first.matchCount());
        assertEquals(1, first.rank());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), first.matchedNumbers());

        IssueResultResponse.Game second = res.games().get(1);
        assertEquals(0, second.matchCount());
        assertTrue(second.bonusMatched());              // 7 이 보너스와 겹치지만 등수에는 영향 없음
        assertNull(second.rank());
        assertEquals("낙첨", second.rankLabel());
        assertTrue(second.matchedNumbers().isEmpty());

        verify(verificationRepo).insertIfAbsent(101L, ROUND, 6, false, 1, "VERIFIED");
        verify(verificationRepo).insertIfAbsent(102L, ROUND, 0, true, null, "VERIFIED");
    }

    @Test
    void 이미_저장된_게임은_다시_저장하지_않고_저장된_값을_쓴다() {
        stubIssue();
        stubDraw();
        WinningVerification stored = mock(WinningVerification.class);          // 가짜 설정은 when(...) 밖에서 먼저 만든다
        when(stored.getIssueNumberId()).thenReturn(101L);
        when(stored.toMatchResult()).thenReturn(new MatchResult(6, false, 1));
        when(verificationRepo.findByIssueNumberIdIn(any())).thenReturn(List.of(stored));

        IssueResultResponse res = service.getResult(CODE, "1.1.1.1");

        assertEquals(1, res.games().get(0).rank());
        verify(verificationRepo, never()).insertIfAbsent(eq(101L), anyInt(), anyInt(), anyBoolean(), any(), anyString());
        verify(verificationRepo).insertIfAbsent(102L, ROUND, 0, true, null, "VERIFIED");   // 없는 게임만 저장
    }

    @Test
    void 가장_높은_등수가_대표_등수가_된다() {
        stubIssue();
        // 당첨번호를 7~11 + 12 로 바꿔서 2게임이 5개 일치(보너스 없음)가 되게 한다
        when(drawRepo.findByRoundNo(ROUND)).thenReturn(Optional.of(
                new DrawRound(ROUND, LocalDate.of(2026, 10, 24), List.of(7, 8, 9, 10, 11, 30), 1, "TEST")));
        when(verificationRepo.findByIssueNumberIdIn(any())).thenReturn(List.of());

        IssueResultResponse res = service.getResult(CODE, "1.1.1.1");

        // 1게임(1~6): 보너스 1 하나만 겹침 → 낙첨 / 2게임(7~12): 5개 일치 → 3등
        assertEquals(3, res.bestRank());
        assertEquals("3등", res.bestRankLabel());
        assertNull(res.games().get(0).rank());
        assertEquals(3, res.games().get(1).rank());
    }

    @Test
    void 없는_코드와_형식이_틀린_코드는_똑같이_404() {
        when(issueRepo.findByIssueCode(CODE)).thenReturn(Optional.empty());

        BusinessException none = assertThrows(BusinessException.class, () -> service.getResult(CODE, "1.1.1.1"));
        BusinessException bad = assertThrows(BusinessException.class, () -> service.getResult("abc", "1.1.1.1"));
        BusinessException nul = assertThrows(BusinessException.class, () -> service.getResult(null, "1.1.1.1"));

        assertEquals(ErrorCode.NOT_FOUND, none.getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, bad.getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, nul.getErrorCode());
        verifyNoInteractions(drawRepo, verificationRepo);
    }

    @Test
    void 조회_횟수를_넘으면_조회_자체를_하지_않는다() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "많아요", "한도"))
                .when(limiter).check(anyString(), anyString(), anyInt());

        BusinessException e = assertThrows(BusinessException.class, () -> service.getResult(CODE, "1.1.1.1"));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, e.getErrorCode());
        verifyNoInteractions(issueRepo, numberRepo, drawRepo, verificationRepo);
    }
}
