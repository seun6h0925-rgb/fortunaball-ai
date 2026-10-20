package com.fortunaball.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.ticket.dto.TicketCheckRequest;
import com.fortunaball.ticket.dto.TicketCheckResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DB 없이 가짜 저장소(Mockito)로 용지 당첨 확인 흐름을 확인한다.
 * ⚠ 실제 용지의 TR 번호·일련번호는 쓰지 않고 가짜 값으로 만든 QR 글자를 쓴다. (GitHub 저장소가 퍼블릭이라서)
 */
class TicketCheckServiceTest {

    /** 실제 용지 구조와 같은 5줄(모두 수동) + 가짜 식별값 */
    private static final String QR = "http://qr.dhlottery.co.kr/?v=1239"
            + "m030614163442m010305091442m041421223637m061416203145m121617262936" + "100000000112345678";

    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final RequestRateLimiter limiter = mock(RequestRateLimiter.class);
    private final RateLimitProperties rateProps = new RateLimitProperties(30, 30, 25, 5);
    private TicketCheckService service;

    @BeforeEach
    void setUp() {
        service = new TicketCheckService(drawRepo, limiter, rateProps);
    }

    private TicketCheckResponse check(String qr) {
        return service.check(new TicketCheckRequest(qr), "1.1.1.1");
    }

    private static BusinessException reject(Runnable action) {
        return assertThrows(BusinessException.class, action::run);
    }

    /** 1239회 당첨번호: 3, 6, 14, 16, 34, 42 / 보너스 1  (A줄이 6개 모두 일치하도록 만든 가짜 당첨번호) */
    private void stubDraw1239() {
        when(drawRepo.findByRoundNo(1239)).thenReturn(Optional.of(
                new DrawRound(1239, LocalDate.of(2026, 8, 29), List.of(3, 6, 14, 16, 34, 42), 1, "TEST")));
    }

    @Test
    void 당첨번호가_있으면_줄마다_등수가_계산된다() {
        stubDraw1239();

        TicketCheckResponse res = check(QR);

        assertTrue(res.drawn());
        assertEquals(1239, res.roundNo());
        assertEquals(LocalDate.of(2026, 8, 29), res.drawDate());
        assertEquals(List.of(3, 6, 14, 16, 34, 42), res.winningNumbers());
        assertEquals(5, res.games().size());

        TicketCheckResponse.Game a = res.games().get(0);
        assertEquals("A", a.line());
        assertEquals("수동", a.kindLabel());
        assertEquals(6, a.matchCount());
        assertEquals(1, a.rank());
        assertEquals("1등", a.rankLabel());

        TicketCheckResponse.Game b = res.games().get(1);           // 3, 14, 42 일치 + 보너스 1 일치
        assertEquals(3, b.matchCount());
        assertTrue(b.bonusMatched());
        assertEquals(5, b.rank());                                  // 보너스는 5개를 맞힌 경우에만 등수에 영향
        assertEquals(List.of(3, 14, 42), b.matchedNumbers());

        TicketCheckResponse.Game e = res.games().get(4);           // 16 하나만 일치 → 낙첨
        assertEquals("E", e.line());
        assertNull(e.rank());
        assertEquals("낙첨", e.rankLabel());

        assertEquals(1, res.bestRank());
        assertEquals("1등", res.bestRankLabel());
        verify(limiter).check("ticket-check", "1.1.1.1", 25);
    }

    @Test
    void 모든_줄이_낙첨이면_대표_등수도_낙첨() {
        when(drawRepo.findByRoundNo(1239)).thenReturn(Optional.of(
                new DrawRound(1239, LocalDate.of(2026, 8, 29), List.of(7, 8, 9, 10, 11, 13), 2, "TEST")));

        TicketCheckResponse res = check(QR);

        assertTrue(res.drawn());
        assertNull(res.bestRank());
        assertEquals("낙첨", res.bestRankLabel());
    }

    @Test
    void 아직_당첨번호가_없는_다음_회차는_오류가_아니라_안내로_답한다() {
        when(drawRepo.findByRoundNo(1239)).thenReturn(Optional.empty());
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(
                new DrawRound(1238, LocalDate.of(2026, 8, 22), List.of(1, 2, 3, 4, 5, 6), 7, "TEST")));

        TicketCheckResponse res = check(QR);

        assertFalse(res.drawn());
        assertEquals(TicketCheckService.MSG_NOT_DRAWN, res.message());
        assertNull(res.winningNumbers());
        assertNull(res.bestRank());
        assertEquals(5, res.games().size());                         // 번호는 그대로 보여준다
        assertEquals(List.of(3, 6, 14, 16, 34, 42), res.games().get(0).numbers());
        assertNull(res.games().get(0).rank());
    }

    @Test
    void 지난_회차인데_당첨번호가_없으면_데이터_누락이라_404() {
        when(drawRepo.findByRoundNo(1239)).thenReturn(Optional.empty());
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(
                new DrawRound(1246, LocalDate.of(2026, 10, 17), List.of(1, 2, 3, 4, 5, 6), 7, "TEST")));

        assertEquals(ErrorCode.NOT_FOUND, reject(() -> check(QR)).getErrorCode());
    }

    @Test
    void 로또_QR이_아니면_400이고_당첨번호는_조회하지_않는다() {
        for (String bad : new String[]{null, "", "hello", "http://example.com/?v=1239m030614163442"}) {
            assertEquals(ErrorCode.INVALID_INPUT, reject(() -> check(bad)).getErrorCode());
        }
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.check(null, "1.1.1.1")).getErrorCode());
        verifyNoInteractions(drawRepo);
    }

    @Test
    void 오류_메시지에는_QR_원문이_들어가지_않는다() {
        BusinessException e = reject(() -> check("http://example.com/?v=1239m030614163442" + "100000000112345678"));
        assertFalse(String.valueOf(e.getMessage()).contains("100000000112345678"));
    }

    @Test
    void 조회_횟수를_넘으면_해석도_조회도_하지_않는다() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "많아요", "한도"))
                .when(limiter).check(anyString(), anyString(), anyInt());

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, reject(() -> check(QR)).getErrorCode());
        verifyNoInteractions(drawRepo);
    }
}
