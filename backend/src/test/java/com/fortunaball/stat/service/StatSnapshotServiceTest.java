package com.fortunaball.stat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.SnapshotStatus;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatSnapshotServiceTest {

    private DrawRoundRepository drawRepo;
    private StatRoundSnapshotRepository snapshotRepo;
    private StatPatternCardRepository cardRepo;
    private StatSnapshotService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 19, 10, 0);

    @BeforeEach
    void setUp() {
        drawRepo = mock(DrawRoundRepository.class);
        snapshotRepo = mock(StatRoundSnapshotRepository.class);
        cardRepo = mock(StatPatternCardRepository.class);
        service = new StatSnapshotService(drawRepo, snapshotRepo, cardRepo,
                new PatternCalculator(), new RoundPolicy(), new ObjectMapper());
        when(snapshotRepo.save(any())).thenAnswer(a -> a.getArgument(0));
    }

    /** 1~latest 회차를 최신순으로 만들어 돌려준다 (번호는 회차에 따라 조금씩 달라지게) */
    private void givenRounds(int latest) {
        List<DrawRound> desc = new ArrayList<>();
        for (int r = latest; r >= 1 && r > latest - 120; r--) {
            int b = r % 30;
            desc.add(new DrawRound(r, LocalDate.of(2020, 1, 1),
                    List.of(1 + b, 3 + b, 5 + b, 7 + b, 9 + b, 11 + b), 45, "test"));
        }
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(desc.get(0)));
        when(drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(eq(latest), any())).thenReturn(desc);
    }

    @Test
    void 저장된_회차가_없으면_아무것도_안_한다() {
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.empty());
        service.sync(1247, now);
        verify(snapshotRepo, never()).save(any());
    }

    @Test
    void 월요일_상황_1246확정_1247사전분석을_만든다() {
        givenRounds(1246);
        when(snapshotRepo.findByTargetRoundNo(anyInt())).thenReturn(Optional.empty());

        service.sync(1247, now);

        ArgumentCaptor<StatRoundSnapshot> cap = ArgumentCaptor.forClass(StatRoundSnapshot.class);
        verify(snapshotRepo, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        StatRoundSnapshot last1246 = cap.getAllValues().stream()
                .filter(s -> s.getTargetRoundNo() == 1246).reduce((a, b) -> b).orElseThrow();
        StatRoundSnapshot last1247 = cap.getAllValues().stream()
                .filter(s -> s.getTargetRoundNo() == 1247).reduce((a, b) -> b).orElseThrow();
        assertEquals(SnapshotStatus.CONFIRMED, last1246.getStatus());
        assertEquals(SnapshotStatus.PREPARING, last1247.getStatus());
        assertEquals(1246, last1247.getBasisRoundNo());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StatPatternCard>> cards = ArgumentCaptor.forClass(List.class);
        verify(cardRepo, org.mockito.Mockito.times(2)).saveAll(cards.capture());
        assertEquals(18, cards.getAllValues().get(0).size());
    }

    @Test
    void 토요일_20시_이후_1247은_결과대기_1248은_사전분석() {
        givenRounds(1246);
        StatRoundSnapshot s1246 = StatRoundSnapshot.prepare(1246, 1246, now);
        s1246.confirm(1246, now);
        StatRoundSnapshot s1247 = StatRoundSnapshot.prepare(1247, 1246, now);
        when(snapshotRepo.findByTargetRoundNo(1246)).thenReturn(Optional.of(s1246));
        when(snapshotRepo.findByTargetRoundNo(1247)).thenReturn(Optional.of(s1247));
        when(snapshotRepo.findByTargetRoundNo(1248)).thenReturn(Optional.empty());

        service.sync(1248, now);

        assertEquals(SnapshotStatus.WAITING, s1247.getStatus());
        // 1246, 1247 은 기준 회차가 그대로라 카드를 다시 만들지 않고, 1248 만 새로 만든다
        verify(cardRepo, org.mockito.Mockito.times(1)).saveAll(any());
    }

    @Test
    void 기준_회차가_바뀌면_카드를_지우고_다시_만든다() {
        givenRounds(1247);
        StatRoundSnapshot s1247 = StatRoundSnapshot.prepare(1247, 1246, now);
        StatRoundSnapshot s1248 = StatRoundSnapshot.prepare(1248, 1246, now);
        when(snapshotRepo.findByTargetRoundNo(1247)).thenReturn(Optional.of(s1247));
        when(snapshotRepo.findByTargetRoundNo(1248)).thenReturn(Optional.of(s1248));

        service.sync(1248, now);

        assertEquals(SnapshotStatus.CONFIRMED, s1247.getStatus());
        assertEquals(1247, s1247.getBasisRoundNo());
        assertEquals(SnapshotStatus.PREPARING, s1248.getStatus());
        assertEquals(1247, s1248.getBasisRoundNo());
        verify(cardRepo, org.mockito.Mockito.times(2)).saveAll(any());
    }
}
