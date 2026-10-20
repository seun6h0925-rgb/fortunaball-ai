package com.fortunaball.generate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.domain.GenerationTicket;
import com.fortunaball.generate.domain.PatternUnavailableException;
import com.fortunaball.generate.dto.GenerateRequest;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.generate.dto.PatternCondition;
import com.fortunaball.issue.repository.ComboIssueCountRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import com.fortunaball.stat.service.PatternRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 번호 생성 API(API-03)의 흐름을 묶는 서비스. DB 에는 아무것도 쓰지 않는다. (읽기 전용)
 * 📚 배운 내용: @Service, @Transactional(readOnly = true), 생성자 주입
 *
 * 흐름
 *  1) 입력 검사 (게임 수 1~최대값, 패턴 코드)
 *  2) 대상 회차를 서버가 직접 계산 (클라이언트가 보낸 회차는 받지 않는다)
 *  3) 그 회차의 통계 스냅샷 + 패턴 카드(기간 100회)에서 조건값(핫넘버·겨울잠 번호)을 읽는다
 *  4) 기준 회차까지 최근 100회로 분포 기준(DrawProfile)을 만든다
 *  5) 이 회차에서 이미 N번(기본 3) 확정 발급된 조합을 읽어 제외 목록으로 쓴다
 *  6) NumberGenerator 로 번호 생성 → GenerationStore 에 30분 보관 → 확정 토큰과 함께 응답
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 패턴 조건이 안 맞는 상황(예: 겨울잠 번호가 하나도 없음)에서는 패턴을 완화하지 않고 CONFLICT(409)로 알린다.
 *    화면에서는 "다른 패턴을 선택해 주세요"로 안내한다.
 *  - 같은 게스트가 다시 생성하면 GenerationStore 가 이전 임시 결과를 버린다. (이전 토큰으로는 확정 불가)
 */
@Service
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    private final RoundPolicy roundPolicy;
    private final StatRoundSnapshotRepository snapshotRepo;
    private final StatPatternCardRepository cardRepo;
    private final DrawRoundRepository drawRepo;
    private final ComboIssueCountRepository comboCountRepo;
    private final NumberGenerator generator;
    private final GenerationStore store;
    private final GenerationProperties props;
    private final ObjectMapper objectMapper;

    public GenerateService(RoundPolicy roundPolicy,
                           StatRoundSnapshotRepository snapshotRepo,
                           StatPatternCardRepository cardRepo,
                           DrawRoundRepository drawRepo,
                           ComboIssueCountRepository comboCountRepo,
                           NumberGenerator generator,
                           GenerationStore store,
                           GenerationProperties props,
                           ObjectMapper objectMapper) {
        this.roundPolicy = roundPolicy;
        this.snapshotRepo = snapshotRepo;
        this.cardRepo = cardRepo;
        this.drawRepo = drawRepo;
        this.comboCountRepo = comboCountRepo;
        this.generator = generator;
        this.store = store;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GenerateResponse generate(GuestToken guest, GenerateRequest request) {
        // 1) 입력 검사
        int gameCount = validateGameCount(request == null ? null : request.gameCount());
        PatternCode code = parsePatternCode(request == null ? null : request.patternCode());
        return generateInternal(guest, gameCount, code, Set.of(), Set.of());
    }

    /**
     * AI 애널리스트용: 질문에서 읽은 게임 수와 포함·제외 번호로 번호를 만든다. (패턴은 쓰지 않는다)
     * 대상 회차·통계 기준·"이미 3번 찬 조합 제외"·임시 보관(확정 토큰)은 번호 생성 화면과 똑같다.
     * 포함·제외 번호는 어떤 경우에도 완화하지 않는다. (통계 필터와 쏠림 감점만 완화된다)
     *
     * @param gameCount 비어 있으면 1게임
     */
    @Transactional(readOnly = true)
    public GenerateResponse generateWithNumbers(GuestToken guest, Integer gameCount, Set<Integer> include, Set<Integer> exclude) {
        int count = validateGameCount(gameCount == null ? Integer.valueOf(1) : gameCount);
        Set<Integer> in = include == null ? Set.of() : include;
        Set<Integer> out = exclude == null ? Set.of() : exclude;
        validateNumberChoices(in, out, count);
        return generateInternal(guest, count, null, in, out);
    }

    private GenerateResponse generateInternal(GuestToken guest, int gameCount, PatternCode code,
                                              Set<Integer> include, Set<Integer> exclude) {
        // 2) 대상 회차는 서버가 계산한다
        ZonedDateTime now = ZonedDateTime.now(RoundPolicy.ZONE);
        int targetRound = roundPolicy.targetRound(now);
        LocalDate drawDate = roundPolicy.drawDate(now);

        // 3) 통계 스냅샷
        StatRoundSnapshot snapshot = snapshotOf(targetRound);
        int basisRound = snapshot.getBasisRoundNo();

        // 4) 기준 회차까지 최근 100회로 분포 기준을 만든다
        DrawProfile profile = DrawProfile.from(loadHistory(basisRound));

        // 3-2) 패턴 조건값 (패턴이 없으면 포함·제외 번호 조건만 담는다)
        PatternCondition condition = code == null
                ? PatternCondition.withNumbers(include, exclude) : loadCondition(snapshot, code);

        // 5) 이미 N번 찬 조합은 제외
        Set<String> excluded = new HashSet<>(comboCountRepo.findKeysAtLimit(targetRound, props.maxIssuePerCombo()));

        // 6) 생성 → 보관
        List<Combo> games = runGenerator(condition, gameCount, profile, excluded, code, targetRound);
        GenerationTicket ticket = store.issue(guest.value(), targetRound, basisRound, code, games);
        log.info("[번호생성] guest={} 대상회차={} 기준회차={} 게임수={} 패턴={}",
                guest, targetRound, basisRound, gameCount, code);

        return new GenerateResponse(
                ticket.confirmToken(),
                props.ttlMinutes(),
                ticket.expiresAt(),
                games.stream().map(Combo::numbers).toList(),
                code == null ? null : code.name(),
                code == null ? null : code.displayName(),
                targetRound,
                drawDate,
                basisRound);
    }

    /**
     * 발급 확정(API-04) 중 "이미 3번 찬 조합"을 교체할 새 번호를 만든다.
     * 처음 생성할 때와 같은 대상 회차·같은 패턴 조건을 그대로 쓰고, 이미 찬 조합 + 호출한 쪽이 넘긴 제외 목록을 뺀다.
     * (임시 결과의 패턴을 지켜야 하므로 패턴은 완화하지 않는다)
     *
     * @param extraExcluded 이번 발급에서 이미 쓴 조합 key 등, 호출한 쪽이 추가로 빼고 싶은 key
     */
    @Transactional(readOnly = true)
    public List<Combo> generateMore(int targetRound, PatternCode code, int count, Set<String> extraExcluded) {
        StatRoundSnapshot snapshot = snapshotOf(targetRound);
        DrawProfile profile = DrawProfile.from(loadHistory(snapshot.getBasisRoundNo()));
        PatternCondition condition = loadCondition(snapshot, code);
        Set<String> excluded = new HashSet<>(comboCountRepo.findKeysAtLimit(targetRound, props.maxIssuePerCombo()));
        excluded.addAll(extraExcluded);
        return runGenerator(condition, count, profile, excluded, code, targetRound);
    }

    private StatRoundSnapshot snapshotOf(int targetRound) {
        return snapshotRepo.findByTargetRoundNo(targetRound)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                        "stat_round_snapshot 없음: targetRound=" + targetRound));
    }

    /**
     * 포함·제외 번호가 서로 맞는지 미리 검사해서, 만들 수 없는 요청을 바로 쉬운 말로 돌려보낸다.
     * (가능한 조합 수 = 포함하고 남은 자리 수만큼을 제외하지 않은 나머지 번호에서 고르는 경우의 수)
     */
    private void validateNumberChoices(Set<Integer> include, Set<Integer> exclude, int gameCount) {
        for (int n : include) {
            if (n < 1 || n > 45) {
                throw invalidNumbers("번호는 1부터 45까지만 쓸 수 있어요.", "포함 번호 범위 오류: " + n);
            }
        }
        for (int n : exclude) {
            if (n < 1 || n > 45) {
                throw invalidNumbers("번호는 1부터 45까지만 쓸 수 있어요.", "제외 번호 범위 오류: " + n);
            }
        }
        for (int n : include) {
            if (exclude.contains(n)) {
                throw invalidNumbers(n + "번을 포함과 제외에 모두 적으셨어요. 하나만 선택해 주세요.",
                        "포함·제외 번호 충돌: " + n);
            }
        }
        if (include.size() > 6) {
            throw invalidNumbers("포함할 번호는 6개까지만 적을 수 있어요.", "포함 번호 개수 초과: " + include.size());
        }
        int remaining = 45 - exclude.size() - include.size();
        int slots = 6 - include.size();
        if (remaining < slots) {
            throw invalidNumbers("제외할 번호가 너무 많아요. 제외하고 남는 번호로 6개를 채울 수 있어야 해요.",
                    "제외 번호 과다: exclude=" + exclude.size() + " include=" + include.size());
        }
        long possible = combinations(remaining, slots);
        if (gameCount > possible) {
            throw invalidNumbers("이 조건으로 만들 수 있는 조합은 " + possible + "개뿐이에요. 게임 수를 줄여 주세요.",
                    "가능한 조합 수 초과: possible=" + possible + " gameCount=" + gameCount);
        }
    }

    private static BusinessException invalidNumbers(String userMessage, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, userMessage, detail);
    }

    /** nCr (n 이 45 이하라 long 으로 충분하다) */
    private static long combinations(int n, int r) {
        long result = 1;
        for (int i = 1; i <= r; i++) {
            result = result * (n - r + i) / i;
        }
        return result;
    }

    private int validateGameCount(Integer gameCount) {
        if (gameCount == null || gameCount < 1 || gameCount > props.maxGames()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "게임 수는 1개부터 " + props.maxGames() + "개까지 선택할 수 있어요.",
                    "gameCount=" + gameCount + " (허용: 1~" + props.maxGames() + ")");
        }
        return gameCount;
    }

    /** 비어 있으면 null(패턴 없음). 모르는 코드는 입력 오류. */
    private PatternCode parsePatternCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PatternCode.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "선택할 수 없는 패턴이에요. 패턴을 다시 선택해 주세요.",
                    "patternCode=" + raw);
        }
    }

    private List<DrawNumbers> loadHistory(int basisRound) {
        List<DrawRound> rounds = drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(
                basisRound, PageRequest.of(0, PatternRule.GENERATION_PERIOD));
        if (rounds.isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                    "당첨 결과 없음: basisRound=" + basisRound);
        }
        return rounds.stream().map(r -> new DrawNumbers(r.getRoundNo(), r.getNumbers())).toList();
    }

    /** 패턴 카드(기간 100회)의 condition_params 에서 핫넘버·겨울잠 번호를 읽는다. 다른 패턴은 번호 목록이 필요 없다. */
    private PatternCondition loadCondition(StatRoundSnapshot snapshot, PatternCode code) {
        if (code == null) {
            return PatternCondition.none();
        }
        StatPatternCard card = cardRepo.findBySnapshotIdAndPeriodRounds(snapshot.getId(), PatternRule.GENERATION_PERIOD)
                .stream().filter(c -> c.getPatternCode() == code).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                        "패턴 카드 없음: snapshotId=" + snapshot.getId() + " code=" + code));
        return switch (code) {
            case HOT_NUMBER -> new PatternCondition(code, readNumbers(card, "hotNumbers"), Set.of());
            case WINTER_SLEEP -> new PatternCondition(code, Set.of(), readNumbers(card, "sleepNumbers"));
            default -> new PatternCondition(code, Set.of(), Set.of());
        };
    }

    private Set<Integer> readNumbers(StatPatternCard card, String field) {
        try {
            JsonNode arr = objectMapper.readTree(card.getConditionParams()).path(field);
            Set<Integer> result = new HashSet<>();
            arr.forEach(n -> result.add(n.asInt()));
            return result;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "번호를 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
                    "condition_params 읽기 실패: cardId=" + card.getId() + " field=" + field + " cause=" + e);
        }
    }

    private List<Combo> runGenerator(PatternCondition condition, int gameCount, DrawProfile profile,
                                     Set<String> excluded, PatternCode code, int targetRound) {
        try {
            return generator.generate(condition, gameCount, profile, excluded);
        } catch (PatternUnavailableException e) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "지금은 이 패턴으로 번호를 만들 수 없어요. 다른 패턴을 선택해 주세요.",
                    "패턴 조건 불충족: code=" + code + " targetRound=" + targetRound + " cause=" + e.getMessage());
        } catch (IllegalStateException e) {
            boolean hasNumberChoices = !condition.includeNumbers().isEmpty() || !condition.excludeNumbers().isEmpty();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    hasNumberChoices ? "조건에 맞는 번호를 만들지 못했어요. 포함·제외 번호나 게임 수를 줄여 주세요."
                            : "번호를 만들지 못했어요. 게임 수를 줄이거나 잠시 후 다시 시도해 주세요.",
                    "후보 부족: code=" + code + " targetRound=" + targetRound + " gameCount=" + gameCount + " cause=" + e.getMessage());
        }
    }
}
