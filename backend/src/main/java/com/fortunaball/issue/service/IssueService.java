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
import com.fortunaball.issue.dto.IssueConfirmResponse.IssuedGame;
import com.fortunaball.issue.dto.IssueConfirmResponse.ReplacedGame;
import com.fortunaball.issue.repository.ComboIssueCountRepository;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 발급 확정(API-04). 번호 생성(API-03)으로 받은 임시 번호를 DB 에 "확정 발급" 기록으로 남긴다.
 * 📚 배운 내용: @Service, TransactionTemplate (코드로 트랜잭션 범위를 직접 정하는 방법, 수집기에서도 사용)
 *
 * 흐름
 *  1) 토큰으로 임시 번호를 찾는다 (없음·만료·다른 게스트 → 409)
 *  2) 대상 회차가 그 사이 바뀌었으면 임시 번호를 버리고 409 (토요일 20:00 이후 확정 방지)
 *  3) 토큰을 꺼낸다 (한 번 꺼내면 지워져서 같은 토큰으로 두 번 확정할 수 없다)
 *  4) 한 트랜잭션 안에서
 *       - 게임마다 조합 횟수를 "3 미만일 때만 +1" 로 확보한다
 *       - 이미 3번 찬 조합은 같은 패턴 조건으로 새 번호를 뽑아 교체한다 (교체 정보를 응답에 담는다)
 *       - 발급 기록 + 번호들을 저장한다
 *  5) 4) 에서 실패하면 저장은 전부 취소되고, 꺼냈던 임시 번호는 되살린다 (같은 토큰으로 다시 시도 가능)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 조합 횟수를 올리는 순서는 "조합 key 오름차순"이다. 두 사람이 동시에 서로 겹치는 조합을 확정할 때
 *    서로 반대 순서로 잠그다가 DB 가 한쪽을 중단시키는 일(교착)을 줄이기 위해서다.
 *  - 교체 번호도 횟수를 확보해야 쓸 수 있다. 확보에 실패하면(그 순간 다른 사람이 먼저 가져감) 다른 번호로 다시 뽑는다.
 *  - 임시 번호가 DB 에 가기 전까지는 "확정"이 아니다. 횟수는 이 서비스에서만 올라간다.
 */
@Service
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);

    /** 교체 번호 하나를 얻으려고 다시 뽑는 최대 횟수 */
    static final int MAX_REPLACE_ATTEMPTS = 10;
    /** 발급 코드가 겹칠 때 다시 만드는 최대 횟수 */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final GenerationStore store;
    private final RoundPolicy roundPolicy;
    private final GenerateService generateService;
    private final ComboIssueCountRepository countRepo;
    private final NumberIssueRepository issueRepo;
    private final IssueNumberRepository numberRepo;
    private final GenerationProperties props;
    private final TransactionTemplate tx;

    public IssueService(GenerationStore store,
                        RoundPolicy roundPolicy,
                        GenerateService generateService,
                        ComboIssueCountRepository countRepo,
                        NumberIssueRepository issueRepo,
                        IssueNumberRepository numberRepo,
                        GenerationProperties props,
                        TransactionTemplate tx) {
        this.store = store;
        this.roundPolicy = roundPolicy;
        this.generateService = generateService;
        this.countRepo = countRepo;
        this.issueRepo = issueRepo;
        this.numberRepo = numberRepo;
        this.props = props;
        this.tx = tx;
    }

    public IssueConfirmResponse confirm(GuestToken guest, IssueConfirmRequest request) {
        String token = request == null ? null : request.confirmToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "확정 정보가 없어요. 번호를 다시 생성해 주세요.", "confirmToken 없음");
        }

        // 1) 임시 번호 찾기
        GenerationTicket found = store.find(token, guest.value())
                .orElseThrow(() -> unusableToken(token));

        // 2) 대상 회차가 바뀌었는지 확인 (서버가 다시 계산한다. 클라이언트가 보낸 회차는 받지 않는다)
        ZonedDateTime now = ZonedDateTime.now(RoundPolicy.ZONE);
        int currentRound = roundPolicy.targetRound(now);
        if (found.targetRound() != currentRound) {
            store.consume(token, guest.value());   // 오래된 임시 번호는 버린다
            throw new BusinessException(ErrorCode.CONFLICT,
                    "회차가 바뀌었어요. 번호를 다시 생성해 주세요.",
                    "대상 회차 변경: ticket=" + found.targetRound() + " 현재=" + currentRound);
        }

        // 3) 토큰 꺼내기 (동시에 두 번 눌러도 한 번만 성공)
        GenerationTicket ticket = store.consume(token, guest.value())
                .orElseThrow(() -> unusableToken(token));

        // 4) DB 저장. 실패하면 임시 번호를 되살린다
        try {
            LocalDate drawDate = roundPolicy.drawDate(now);
            IssueConfirmResponse response = tx.execute(status -> persist(guest, ticket, drawDate));
            log.info("[발급확정] guest={} 대상회차={} 게임수={} 교체={}건 코드={}",
                    guest, ticket.targetRound(), ticket.games().size(),
                    response.replacedGames().size(), response.issueCode());
            return response;
        } catch (RuntimeException e) {
            store.restore(ticket);
            throw e;
        }
    }

    /** (트랜잭션 안) 횟수 확보 → 교체 → 저장 */
    private IssueConfirmResponse persist(GuestToken guest, GenerationTicket ticket, LocalDate drawDate) {
        int round = ticket.targetRound();
        int limit = props.maxIssuePerCombo();
        List<Combo> games = ticket.games();
        int n = games.size();

        Combo[] result = new Combo[n];
        List<Integer> needReplace = new ArrayList<>();
        Set<String> used = new HashSet<>();            // 이번 발급에서 이미 쓴(또는 쓸 수 없는) 조합 key
        games.forEach(g -> used.add(g.key()));

        // (가) 원래 번호: key 오름차순으로 횟수 확보를 시도 (교착 방지)
        List<Integer> claimOrder = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparing(i -> games.get(i).key())).toList();
        for (int i : claimOrder) {
            if (claim(round, games.get(i).key(), limit)) {
                result[i] = games.get(i);
            } else {
                needReplace.add(i);
            }
        }

        // (나) 이미 찬 조합은 게임 순서대로 새 번호로 교체
        needReplace.sort(Comparator.naturalOrder());
        List<ReplacedGame> replaced = new ArrayList<>();
        for (int i : needReplace) {
            Combo before = games.get(i);
            Combo after = replace(ticket, used);
            result[i] = after;
            replaced.add(new ReplacedGame(i + 1, before.numbers(), after.numbers()));
        }

        // (다) 발급 기록 + 번호 저장
        PatternCode code = ticket.patternCode();
        NumberIssue issue = issueRepo.save(NumberIssue.create(
                newIssueCode(), guest.value(), round, n,
                code == null ? IssueType.STAT_AUTO : IssueType.STAT_CUSTOM,
                code == null ? null : "STAT_PATTERN",
                code == null ? null : code.name()));

        List<IssueNumber> rows = new ArrayList<>();
        List<IssuedGame> issued = new ArrayList<>();
        Set<Integer> replacedIndexes = new HashSet<>(needReplace);
        for (int i = 0; i < n; i++) {
            rows.add(IssueNumber.of(issue.getId(), i + 1, result[i].key(), result[i].numbers()));
            issued.add(new IssuedGame(i + 1, result[i].numbers(), replacedIndexes.contains(i)));
        }
        numberRepo.saveAll(rows);

        return new IssueConfirmResponse(issue.getIssueCode(), round, drawDate, n,
                code == null ? null : code.name(),
                code == null ? null : code.displayName(),
                issued, replaced);
    }

    /** 조합 횟수를 1 올릴 수 있으면 true. (행이 없으면 먼저 만든 뒤 "limit 미만일 때만 +1") */
    private boolean claim(int round, String key, int limit) {
        countRepo.ensureRow(round, key);
        return countRepo.incrementIfBelow(round, key, limit) == 1;
    }

    /** 같은 패턴 조건으로 새 번호를 뽑아 횟수 확보까지 성공한 조합을 돌려준다 */
    private Combo replace(GenerationTicket ticket, Set<String> used) {
        for (int attempt = 0; attempt < MAX_REPLACE_ATTEMPTS; attempt++) {
            Combo candidate = generateService.generateMore(
                    ticket.targetRound(), ticket.patternCode(), 1, used).get(0);
            used.add(candidate.key());                  // 성공하든 실패하든 같은 번호를 다시 뽑지 않는다
            if (claim(ticket.targetRound(), candidate.key(), props.maxIssuePerCombo())) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                "번호를 확정하지 못했어요. 잠시 후 다시 시도해 주세요.",
                "교체 번호 확보 실패: targetRound=" + ticket.targetRound() + " 시도=" + MAX_REPLACE_ATTEMPTS);
    }

    private String newIssueCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = IssueCodeGenerator.next();
            if (!issueRepo.existsByIssueCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "번호를 확정하지 못했어요. 잠시 후 다시 시도해 주세요.", "발급 코드 중복이 계속됨");
    }

    private static BusinessException unusableToken(String token) {
        return new BusinessException(ErrorCode.CONFLICT,
                "임시 번호가 만료되었거나 이미 사용되었어요. 번호를 다시 생성해 주세요.",
                "임시 토큰 없음/만료/재사용/다른 게스트: tokenLength=" + token.length());
    }
}
