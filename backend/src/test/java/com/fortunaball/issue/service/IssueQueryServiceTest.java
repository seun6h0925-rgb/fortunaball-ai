package com.fortunaball.issue.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.dto.IssueDetailResponse;
import com.fortunaball.issue.dto.IssueListResponse;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.IssueNumberRepository.VerificationCount;
import com.fortunaball.issue.repository.NumberIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DB 없이 가짜 저장소(Mockito)로 발급 내역 조회 흐름을 확인한다. (집계 쿼리 자체는 IssueRepositoryTest 가 실제 DB 로 확인) */
class IssueQueryServiceTest {

    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");
    private static final String CODE = "ABCDEFGHJKMN";

    private final NumberIssueRepository issueRepo = mock(NumberIssueRepository.class);
    private final IssueNumberRepository numberRepo = mock(IssueNumberRepository.class);
    private final RequestRateLimiter limiter = mock(RequestRateLimiter.class);
    private final RateLimitProperties rateProps = new RateLimitProperties(30, 30, 30);
    private IssueQueryService service;

    @BeforeEach
    void setUp() {
        service = new IssueQueryService(issueRepo, numberRepo, limiter, rateProps);
    }

    private NumberIssue issue(long id, String code, String basisValue) {
        NumberIssue n = NumberIssue.create(code, GUEST.value(), 1247, 2,
                basisValue == null ? IssueType.STAT_AUTO : IssueType.STAT_CUSTOM,
                basisValue == null ? null : "STAT_PATTERN", basisValue);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    private VerificationCount count(long issueId, long total, long verified, long error) {
        VerificationCount c = mock(VerificationCount.class);
        when(c.getIssueId()).thenReturn(issueId);
        when(c.getTotalGames()).thenReturn(total);
        when(c.getVerifiedGames()).thenReturn(verified);
        when(c.getErrorGames()).thenReturn(error);
        return c;
    }

    private void stubDetail(NumberIssue issue) {
        when(issueRepo.findByIssueCode(CODE)).thenReturn(Optional.of(issue));
        when(numberRepo.findByNumberIssueIdOrderByGameOrderAsc(issue.getId())).thenReturn(List.of(
                IssueNumber.of(issue.getId(), 1, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6)),
                IssueNumber.of(issue.getId(), 2, "07-08-09-10-11-12", List.of(7, 8, 9, 10, 11, 12))));
    }

    // ---------- 상세 (API-05) ----------

    @Test
    void 상세는_번호와_회차를_돌려주고_추첨_전이면_검증_대기() {
        stubDetail(issue(10L, CODE, "HOT_NUMBER"));
        when(numberRepo.countVerification(any())).thenReturn(List.of());

        IssueDetailResponse res = service.getDetail(CODE, "1.1.1.1");

        assertEquals(CODE, res.issueCode());
        assertEquals(1247, res.targetRound());
        assertEquals(2, res.games().size());
        assertEquals(List.of(7, 8, 9, 10, 11, 12), res.games().get(1).numbers());
        assertEquals(2, res.games().get(1).gameOrder());
        assertEquals("HOT_NUMBER", res.patternCode());
        assertEquals("PENDING", res.verificationStatus());
        assertEquals("검증 대기", res.verificationLabel());
        verify(limiter).check("issue-lookup", "1.1.1.1", 30);
    }

    @Test
    void 모든_게임이_검증됐으면_검증_완료() {
        stubDetail(issue(10L, CODE, null));
        VerificationCount allDone = count(10L, 2, 2, 0);      // 가짜 설정은 when(...) 밖에서 먼저 만든다 (안에서 만들면 Mockito 오류)
        when(numberRepo.countVerification(any())).thenReturn(List.of(allDone));
        IssueDetailResponse res = service.getDetail(CODE, "1.1.1.1");
        assertEquals("VERIFIED", res.verificationStatus());
        assertNull(res.patternCode());
    }

    @Test
    void 코드는_소문자와_앞뒤_공백이_있어도_찾는다() {
        stubDetail(issue(10L, CODE, null));
        when(numberRepo.countVerification(any())).thenReturn(List.of());
        assertEquals(CODE, service.getDetail("  " + CODE.toLowerCase() + " ", "1.1.1.1").issueCode());
    }

    @Test
    void 없는_코드와_형식이_틀린_코드는_똑같이_404이고_형식이_틀리면_DB를_조회하지_않는다() {
        when(issueRepo.findByIssueCode(anyString())).thenReturn(Optional.empty());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.getDetail("ZZZZZZZZZZZZ", "1.1.1.1")).getErrorCode());

        for (String bad : new String[]{"짧다", "ABC", "ABCDEFGHJKM0", "ABCDEFGHJKMNP", null}) {
            assertEquals(ErrorCode.NOT_FOUND,
                    assertThrows(BusinessException.class, () -> service.getDetail(bad, "1.1.1.1")).getErrorCode());
        }
        verify(issueRepo).findByIssueCode("ZZZZZZZZZZZZ");          // 형식이 맞는 코드만 DB 조회
        verify(issueRepo, never()).findByIssueCode("ABC");
    }

    @Test
    void 조회_횟수를_넘으면_429이고_DB를_조회하지_않는다() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "많아요"))
                .when(limiter).check(anyString(), anyString(), anyInt());
        assertEquals(ErrorCode.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> service.getDetail(CODE, "1.1.1.1")).getErrorCode());
        verify(issueRepo, never()).findByIssueCode(anyString());
    }

    // ---------- 목록 (API-42) ----------

    @Test
    void 목록은_이_기기의_발급을_최신순으로_담고_건별_검증_상태를_붙인다() {
        NumberIssue a = issue(2L, "BBBBBBBBBBBB", "BALANCE_MIX");
        NumberIssue b = issue(1L, "AAAAAAAAAAAA", null);
        when(issueRepo.findByGuestTokenOrderByCreatedAtDescIdDesc(eq(GUEST.value()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2));
        VerificationCount firstDone = count(1L, 2, 2, 0);
        when(numberRepo.countVerification(any())).thenReturn(List.of(firstDone));   // 2번 건은 결과 없음 → 대기

        IssueListResponse res = service.getList(GUEST, null, null);

        assertEquals(2, res.items().size());
        assertEquals("BBBBBBBBBBBB", res.items().get(0).issueCode());
        assertEquals("PENDING", res.items().get(0).verificationStatus());
        assertEquals("BALANCE_MIX", res.items().get(0).patternCode());
        assertEquals("VERIFIED", res.items().get(1).verificationStatus());
        assertEquals(0, res.page());
        assertEquals(20, res.size());
        assertEquals(2, res.totalCount());
        assertEquals(1, res.totalPages());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(numberRepo).countVerification(ids.capture());
        assertEquals(List.of(2L, 1L), List.copyOf(ids.getValue()));    // 건마다 따로 묻지 않고 한 번에 묻는다
    }

    @Test
    void 발급이_없으면_빈_목록이고_집계_쿼리를_부르지_않는다() {
        when(issueRepo.findByGuestTokenOrderByCreatedAtDescIdDesc(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        IssueListResponse res = service.getList(GUEST, null, null);
        assertEquals(0, res.items().size());
        assertEquals(0, res.totalCount());
        verify(numberRepo, never()).countVerification(any());
    }

    @Test
    void 쪽_번호와_크기를_받아_그대로_조회하고_잘못된_값은_400() {
        when(issueRepo.findByGuestTokenOrderByCreatedAtDescIdDesc(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));
        service.getList(GUEST, "2", "5");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(issueRepo).findByGuestTokenOrderByCreatedAtDescIdDesc(eq(GUEST.value()), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());

        String[][] bad = {{"-1", null}, {"abc", null}, {null, "0"}, {null, "51"}, {null, "x"}};
        for (String[] p : bad) {
            assertEquals(ErrorCode.INVALID_INPUT,
                    assertThrows(BusinessException.class, () -> service.getList(GUEST, p[0], p[1])).getErrorCode());
        }
        service.getList(GUEST, "0", "50");     // 경계값 통과
        service.getList(GUEST, "0", "1");
    }
}
