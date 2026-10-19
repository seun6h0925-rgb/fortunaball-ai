package com.fortunaball.issue.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.domain.GenerationTicket;
import com.fortunaball.generate.service.GenerateService;
import com.fortunaball.generate.service.GenerationProperties;
import com.fortunaball.generate.service.GenerationStore;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.dto.IssueConfirmRequest;
import com.fortunaball.issue.dto.IssueConfirmResponse;
import com.fortunaball.issue.repository.ComboIssueCountRepository;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DB 없이 가짜 저장소(Mockito)로 발급 확정 흐름을 확인한다. (횟수 올리기 자체는 IssueRepositoryTest 가 실제 DB 로 확인) */
class IssueServiceTest {

    private static final int TARGET = 1247;
    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");
    private static final GuestToken OTHER = GuestToken.of("guest-bbbbbbbbbbbbbbbb");

    private final GenerationProperties props = new GenerationProperties(30, 50, 3);
    private final GenerationStore store = new GenerationStore(props);
    private final RoundPolicy roundPolicy = mock(RoundPolicy.class);
    private final GenerateService generateService = mock(GenerateService.class);
    private final ComboIssueCountRepository countRepo = mock(ComboIssueCountRepository.class);
    private final NumberIssueRepository issueRepo = mock(NumberIssueRepository.class);
    private final IssueNumberRepository numberRepo = mock(IssueNumberRepository.class);
    private IssueService service;

    private final Combo c1 = Combo.of(List.of(1, 2, 3, 4, 5, 6));
    private final Combo c2 = Combo.of(List.of(7, 8, 9, 10, 11, 12));
    private final Combo c3 = Combo.of(List.of(13, 14, 15, 16, 17, 18));

    /** 트랜잭션 없이 콜백만 실행하는 가짜 (실제 롤백은 확인하지 않는다) */
    private static TransactionTemplate passThrough() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                TransactionStatus status = new SimpleTransactionStatus();
                return action.doInTransaction(status);
            }
        };
    }

    @BeforeEach
    void setUp() {
        service = new IssueService(store, roundPolicy, generateService, countRepo, issueRepo, numberRepo, props, passThrough());
        when(roundPolicy.targetRound(any(ZonedDateTime.class))).thenReturn(TARGET);
        when(roundPolicy.drawDate(any(ZonedDateTime.class))).thenReturn(LocalDate.of(2026, 10, 24));
        when(countRepo.incrementIfBelow(anyInt(), anyString(), anyInt())).thenReturn(1);   // 기본: 횟수 확보 성공
        when(issueRepo.save(any(NumberIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepo.existsByIssueCode(anyString())).thenReturn(false);
    }

    private GenerationTicket ticket(PatternCode code, Combo... games) {
        return store.issue(GUEST.value(), TARGET, 1246, code, List.of(games));
    }

    private BusinessException conflict(String token, GuestToken guest) {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.confirm(guest, new IssueConfirmRequest(token)));
        assertEquals(ErrorCode.CONFLICT, e.getErrorCode());
        return e;
    }

    @Test
    void 정상_확정이면_기록과_번호가_저장되고_토큰은_사라진다() {
        GenerationTicket t = ticket(null, c1, c2, c3);
        IssueConfirmResponse res = service.confirm(GUEST, new IssueConfirmRequest(t.confirmToken()));

        assertEquals(12, res.issueCode().length());
        assertEquals(TARGET, res.targetRound());
        assertEquals(3, res.gameCount());
        assertTrue(res.replacedGames().isEmpty());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), res.games().get(0).numbers());
        assertFalse(res.games().get(0).replaced());

        ArgumentCaptor<NumberIssue> issue = ArgumentCaptor.forClass(NumberIssue.class);
        verify(issueRepo).save(issue.capture());
        assertEquals(IssueType.STAT_AUTO, issue.getValue().getIssueType());
        assertEquals(GUEST.value(), issue.getValue().getGuestToken());
        assertEquals(3, issue.getValue().getGameCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IssueNumber>> rows = ArgumentCaptor.forClass(List.class);
        verify(numberRepo).saveAll(rows.capture());
        assertEquals(3, rows.getValue().size());
        assertEquals(c1.key(), rows.getValue().get(0).getComboKey());
        assertEquals(1, rows.getValue().get(0).getGameOrder());

        verify(countRepo, times(3)).incrementIfBelow(eq(TARGET), anyString(), eq(3));
        assertTrue(store.find(t.confirmToken(), GUEST.value()).isEmpty());   // 토큰은 한 번만
    }

    @Test
    void 패턴을_골랐다면_패턴_정보가_기록된다() {
        GenerationTicket t = ticket(PatternCode.HOT_NUMBER, c1);
        IssueConfirmResponse res = service.confirm(GUEST, new IssueConfirmRequest(t.confirmToken()));
        assertEquals("HOT_NUMBER", res.patternCode());

        ArgumentCaptor<NumberIssue> issue = ArgumentCaptor.forClass(NumberIssue.class);
        verify(issueRepo).save(issue.capture());
        assertEquals(IssueType.STAT_CUSTOM, issue.getValue().getIssueType());
        assertEquals("STAT_PATTERN", issue.getValue().getGenerationBasisType());
        assertEquals("HOT_NUMBER", issue.getValue().getGenerationBasisValue());
    }

    @Test
    void 이미_3번_찬_조합은_새_번호로_교체되고_교체_정보가_담긴다() {
        GenerationTicket t = ticket(PatternCode.BALANCE_MIX, c1, c2, c3);
        when(countRepo.incrementIfBelow(TARGET, c2.key(), 3)).thenReturn(0);             // 두 번째 게임이 이미 찼다
        Combo replacement = Combo.of(List.of(20, 21, 22, 30, 31, 40));
        when(generateService.generateMore(eq(TARGET), eq(PatternCode.BALANCE_MIX), eq(1), anySet()))
                .thenReturn(List.of(replacement));

        IssueConfirmResponse res = service.confirm(GUEST, new IssueConfirmRequest(t.confirmToken()));

        assertEquals(1, res.replacedGames().size());
        assertEquals(2, res.replacedGames().get(0).gameOrder());
        assertEquals(c2.numbers(), res.replacedGames().get(0).before());
        assertEquals(replacement.numbers(), res.replacedGames().get(0).after());
        assertTrue(res.games().get(1).replaced());
        assertFalse(res.games().get(0).replaced());
        assertEquals(replacement.numbers(), res.games().get(1).numbers());
        verify(countRepo).incrementIfBelow(TARGET, replacement.key(), 3);   // 교체 번호도 횟수를 확보했다
    }

    @Test
    void 교체_번호도_찼다면_다른_번호로_다시_뽑는다() {
        GenerationTicket t = ticket(null, c1);
        when(countRepo.incrementIfBelow(TARGET, c1.key(), 3)).thenReturn(0);
        Combo full = Combo.of(List.of(20, 21, 22, 30, 31, 40));
        Combo ok = Combo.of(List.of(2, 4, 6, 8, 10, 45));
        when(countRepo.incrementIfBelow(TARGET, full.key(), 3)).thenReturn(0);
        when(generateService.generateMore(eq(TARGET), any(), eq(1), anySet()))
                .thenReturn(List.of(full)).thenReturn(List.of(ok));

        IssueConfirmResponse res = service.confirm(GUEST, new IssueConfirmRequest(t.confirmToken()));
        assertEquals(ok.numbers(), res.games().get(0).numbers());
    }

    @Test
    void 토큰이_없거나_모르는_토큰이거나_남의_토큰이면_거절() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.confirm(GUEST, new IssueConfirmRequest(" ")));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.confirm(GUEST, null)).getErrorCode());

        conflict("없는토큰", GUEST);

        GenerationTicket t = ticket(null, c1);
        conflict(t.confirmToken(), OTHER);                                   // 남이 시도
        assertTrue(store.find(t.confirmToken(), GUEST.value()).isPresent()); // 내 것은 그대로
    }

    @Test
    void 같은_토큰으로_두번_확정하면_두번째는_거절() {
        GenerationTicket t = ticket(null, c1);
        service.confirm(GUEST, new IssueConfirmRequest(t.confirmToken()));
        conflict(t.confirmToken(), GUEST);
        verify(issueRepo, times(1)).save(any(NumberIssue.class));
    }

    @Test
    void 회차가_바뀌었으면_거절하고_임시_번호를_버린다() {
        GenerationTicket t = ticket(null, c1);
        when(roundPolicy.targetRound(any(ZonedDateTime.class))).thenReturn(TARGET + 1);
        conflict(t.confirmToken(), GUEST);
        assertTrue(store.find(t.confirmToken(), GUEST.value()).isEmpty());
        verify(issueRepo, times(0)).save(any(NumberIssue.class));
    }

    @Test
    void 저장_중_오류가_나면_임시_번호를_되살려_다시_시도할_수_있다() {
        GenerationTicket t = ticket(null, c1);
        when(issueRepo.save(any(NumberIssue.class))).thenThrow(new IllegalStateException("DB 오류"));
        assertThrows(IllegalStateException.class, () -> service.confirm(GUEST, new IssueConfirmRequest(t.confirmToken())));
        assertTrue(store.find(t.confirmToken(), GUEST.value()).isPresent());

        when(issueRepo.save(any(NumberIssue.class))).thenAnswer(inv -> inv.getArgument(0));   // 이번엔 성공
        service.confirm(GUEST, new IssueConfirmRequest(t.confirmToken()));
    }

    @Test
    void 오류_후_그_사이_새로_생성했다면_이전_번호는_되살리지_않는다() {
        GenerationTicket old = ticket(null, c1);
        when(issueRepo.save(any(NumberIssue.class))).thenAnswer(inv -> {
            ticket(null, c2);                                // 처리 중에 같은 게스트가 새로 생성
            throw new IllegalStateException("DB 오류");
        });
        assertThrows(IllegalStateException.class, () -> service.confirm(GUEST, new IssueConfirmRequest(old.confirmToken())));
        assertTrue(store.find(old.confirmToken(), GUEST.value()).isEmpty());
    }
}
