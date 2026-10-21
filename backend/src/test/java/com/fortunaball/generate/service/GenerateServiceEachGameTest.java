package com.fortunaball.generate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.issue.repository.ComboIssueCountRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import com.fortunaball.stat.service.PatternRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 꿈풀이용 번호 생성(게임마다 다른 포함 번호) 흐름을 가짜 저장소로 확인한다. */
class GenerateServiceEachGameTest {

    private static final int TARGET = 1247;
    private static final int BASIS = 1246;
    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");

    private final RoundPolicy roundPolicy = mock(RoundPolicy.class);
    private final StatRoundSnapshotRepository snapshotRepo = mock(StatRoundSnapshotRepository.class);
    private final StatPatternCardRepository cardRepo = mock(StatPatternCardRepository.class);
    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final ComboIssueCountRepository comboRepo = mock(ComboIssueCountRepository.class);
    private final GenerationProperties props = new GenerationProperties(30, 50, 3);
    private final GenerationStore store = new GenerationStore(props);
    private GenerateService service;

    @BeforeEach
    void setUp() {
        service = new GenerateService(roundPolicy, snapshotRepo, cardRepo, drawRepo, comboRepo,
                new NumberGenerator(), store, props, new ObjectMapper());

        when(roundPolicy.targetRound(any(ZonedDateTime.class))).thenReturn(TARGET);
        when(roundPolicy.drawDate(any(ZonedDateTime.class))).thenReturn(LocalDate.of(2026, 10, 24));
        when(snapshotRepo.findByTargetRoundNo(TARGET))
                .thenReturn(Optional.of(StatRoundSnapshot.prepare(TARGET, BASIS, LocalDateTime.now())));
        when(drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(anyInt(), any(Pageable.class))).thenReturn(history());
        when(comboRepo.findKeysAtLimit(anyInt(), anyInt())).thenReturn(List.of());
    }

    private static List<DrawRound> history() {
        Random r = new Random(5);
        List<DrawRound> list = new ArrayList<>();
        for (int round = BASIS; round > BASIS - 100; round--) {
            List<Integer> nums = new ArrayList<>();
            while (nums.size() < 7) {
                int n = 1 + r.nextInt(45);
                if (!nums.contains(n)) {
                    nums.add(n);
                }
            }
            list.add(new DrawRound(round, LocalDate.of(2026, 1, 1), nums.subList(0, 6), nums.get(6), "TEST"));
        }
        return list;
    }

    private void cards(PatternCode code, String json) {
        when(cardRepo.findBySnapshotIdAndPeriodRounds(any(), anyInt())).thenReturn(
                List.of(new StatPatternCard(1L, code, code.displayName(), 100, 10, json)));
    }

    private static BusinessException error(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    @Test
    void 게임마다_정한_포함_번호를_지키고_확정_토큰은_하나다() {
        GenerateResponse res = service.generateEachWithNumbers(GUEST, List.of(Set.of(7, 8), Set.of(20), Set.of()));

        assertEquals(3, res.games().size());
        assertEquals(TARGET, res.targetRound());
        assertNull(res.patternCode());
        assertNotNull(res.confirmToken());
        assertTrue(store.find(res.confirmToken(), GUEST.value()).isPresent());
        assertTrue(res.games().get(0).containsAll(List.of(7, 8)));
        assertTrue(res.games().get(1).contains(20));
        res.games().forEach(g -> assertEquals(6, g.size()));
    }

    @Test
    void 게임끼리_같은_조합이_나오지_않는다() {
        GenerateResponse res = service.generateEachWithNumbers(GUEST, List.of(Set.of(7), Set.of(7), Set.of(7), Set.of(7)));

        assertEquals(4, res.games().stream().map(List::toString).distinct().count());
    }

    @Test
    void 포함_번호가_6개인_게임도_만들_수_있다() {
        GenerateResponse res = service.generateEachWithNumbers(GUEST, List.of(Set.of(1, 2, 3, 4, 5, 6), Set.of(7)));

        assertEquals(List.of(1, 2, 3, 4, 5, 6), res.games().get(0));
    }

    @Test
    void 게임_수가_없거나_상한을_넘으면_거절() {
        assertEquals(ErrorCode.INVALID_INPUT, error(() -> service.generateEachWithNumbers(GUEST, null)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, error(() -> service.generateEachWithNumbers(GUEST, List.of())).getErrorCode());
        List<Set<Integer>> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(Set.of());
        }
        assertEquals(ErrorCode.INVALID_INPUT, error(() -> service.generateEachWithNumbers(GUEST, tooMany)).getErrorCode());
    }

    @Test
    void 한_게임의_포함_번호가_6개를_넘거나_범위_밖이면_거절() {
        assertEquals(ErrorCode.INVALID_INPUT,
                error(() -> service.generateEachWithNumbers(GUEST, List.of(Set.of(1, 2, 3, 4, 5, 6, 7)))).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT,
                error(() -> service.generateEachWithNumbers(GUEST, List.of(Set.of(46)))).getErrorCode());
    }
}
