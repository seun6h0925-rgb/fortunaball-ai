package com.fortunaball.issue.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.dto.IssueHideResponse;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 발급 내역 지우기(API-67): 내 기기 것만, 기록은 남기고 숨김 표시만 한다. (DB 없이 가짜 저장소 사용) */
class IssueHideTest {

    private static final GuestToken ME = GuestToken.of("guest-aaaaaaaaaaaaaaaa");
    private static final GuestToken OTHER = GuestToken.of("guest-bbbbbbbbbbbbbbbb");
    private static final String CODE = "ABCDEFGHJKMN";

    private final NumberIssueRepository issueRepo = mock(NumberIssueRepository.class);
    private IssueQueryService service;

    @BeforeEach
    void setUp() {
        service = new IssueQueryService(issueRepo, mock(IssueNumberRepository.class),
                mock(RequestRateLimiter.class), mock(RateLimitProperties.class));
    }

    private NumberIssue issueOf(GuestToken guest) {
        NumberIssue n = NumberIssue.create(CODE, guest.value(), 1247, 2, IssueType.STAT_AUTO, null, null);
        when(issueRepo.findByIssueCode(CODE)).thenReturn(Optional.of(n));
        return n;
    }

    @Test
    void 내_발급_건을_지우면_숨김_시각이_들어가고_기록은_삭제하지_않는다() {
        NumberIssue n = issueOf(ME);
        assertNull(n.getHiddenAt());

        IssueHideResponse res = service.hide(ME, CODE);

        assertEquals(CODE, res.issueCode());
        assertNotNull(n.getHiddenAt());
        verify(issueRepo, never()).delete(n);
    }

    @Test
    void 코드는_소문자와_공백이_있어도_찾는다() {
        issueOf(ME);
        assertEquals(CODE, service.hide(ME, "  " + CODE.toLowerCase() + " ").issueCode());
    }

    @Test
    void 이미_지운_건을_또_지워도_성공이고_처음_지운_시각이_유지된다() {
        NumberIssue n = issueOf(ME);
        service.hide(ME, CODE);
        LocalDateTime first = n.getHiddenAt();

        service.hide(ME, CODE);

        assertEquals(first, n.getHiddenAt());
    }

    @Test
    void 다른_기기의_발급_건은_지울_수_없고_없는_코드와_같은_404() {
        NumberIssue n = issueOf(OTHER);

        BusinessException e = assertThrows(BusinessException.class, () -> service.hide(ME, CODE));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        assertNull(n.getHiddenAt());
    }

    @Test
    void 없는_코드와_형식이_틀린_코드는_404이고_형식이_틀리면_DB를_조회하지_않는다() {
        when(issueRepo.findByIssueCode(anyString())).thenReturn(Optional.empty());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.hide(ME, "ZZZZZZZZZZZZ")).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.hide(ME, "ABC")).getErrorCode());
        verify(issueRepo, never()).findByIssueCode("ABC");
    }
}
