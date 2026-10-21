package com.fortunaball.like.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.like.dto.LikeStatusResponse;
import com.fortunaball.like.repository.LikeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class LikeServiceTest {

    private static final String TOKEN = "abcd1234-abcd-1234-abcd-1234567890ab";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    private final LikeRepository repo = mock(LikeRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    // 2026-10-21 12:00 한국 시간
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), LikeService.ZONE);
    private final LikeService service = new LikeService(repo, tx, clock, 5, 30);

    @BeforeEach
    void setUp() {
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    @Test
    void 누르면_전체_수가_오르고_남은_횟수가_줄어든다() {
        when(repo.tryIncrement(any(), startsWith("G:"), anyInt())).thenReturn(true);
        when(repo.tryIncrement(any(), startsWith("I:"), anyInt())).thenReturn(true);
        when(repo.incrementTotal()).thenReturn(128L);
        when(repo.todayCount(any(), startsWith("G:"))).thenReturn(2);

        LikeStatusResponse r = service.press(TOKEN, "10.0.0.1");

        assertEquals(new LikeStatusResponse(128L, 2, 5, 3), r);
        verify(repo).tryIncrement(TODAY, "G:" + LikeService.sha256(TOKEN), 5);
    }

    @Test
    void 토큰과_접속지_원문은_저장하지_않고_해시만_쓴다() {
        when(repo.tryIncrement(any(), anyString(), anyInt())).thenReturn(true);
        when(repo.incrementTotal()).thenReturn(1L);

        service.press(TOKEN, "10.0.0.1");

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repo, org.mockito.Mockito.times(2)).tryIncrement(any(), keys.capture(), anyInt());
        for (String k : keys.getAllValues()) {
            assertEquals(false, k.contains(TOKEN) || k.contains("10.0.0.1"));
        }
    }

    @Test
    void 사용자_한도에_닿으면_429이고_전체_수는_오르지_않는다() {
        when(repo.tryIncrement(any(), startsWith("G:"), anyInt())).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class, () -> service.press(TOKEN, "10.0.0.1"));

        assertEquals(true, e.getMessage().contains("5번"));
        verify(repo, never()).incrementTotal();
        verify(repo, never()).tryIncrement(any(), startsWith("I:"), anyInt());
    }

    @Test
    void 접속지_한도에_닿으면_429이고_전체_수는_오르지_않는다() {
        when(repo.tryIncrement(any(), startsWith("G:"), anyInt())).thenReturn(true);
        when(repo.tryIncrement(any(), startsWith("I:"), anyInt())).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.press(TOKEN, "10.0.0.1"));

        verify(repo, never()).incrementTotal();
    }

    @Test
    void 현황은_전체_수와_오늘_횟수를_보여준다() {
        when(repo.total()).thenReturn(40L);
        when(repo.todayCount(any(), startsWith("G:"))).thenReturn(5);

        assertEquals(new LikeStatusResponse(40L, 5, 5, 0), service.status(TOKEN));
    }

    @Test
    void 오래된_기록은_7일_이전을_지운다() {
        service.purgeOld();

        verify(repo).deleteBefore(TODAY.minusDays(7));
    }
}
