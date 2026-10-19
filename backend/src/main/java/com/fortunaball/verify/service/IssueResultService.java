package com.fortunaball.verify.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.domain.VerificationStatus;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.verify.domain.MatchResult;
import com.fortunaball.verify.domain.VerificationResultStatus;
import com.fortunaball.verify.domain.WinningRankRule;
import com.fortunaball.verify.domain.WinningVerification;
import com.fortunaball.verify.dto.IssueResultResponse;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 발급번호 당첨 검증 결과 조회 (API-06).
 * 📚 배운 내용: @Service, @Transactional
 *
 * 흐름
 *  1) 조회 횟수 제한 확인 → 발급코드로 발급 건 찾기 (형식 오류/없는 코드는 똑같이 404)
 *  2) 대상 회차의 추첨 결과(draw_round)가 없으면 → "검증 대기"로 답한다. (아무것도 저장하지 않는다)
 *  3) 추첨 결과가 있으면 → 게임마다 저장된 검증 결과를 쓰고, 없는 게임만 계산해서 저장한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 등수는 서버가 번호를 비교해서만 정한다. (WinningRankRule) 화면·AI·사용자 입력으로 정하지 않는다.
 *  - 한 번 저장된 결과는 바꾸지 않는다. (insertIfAbsent) 같은 번호를 동시에 조회해도 결과는 한 건이다.
 *    같은 입력이면 계산 결과도 같으므로, 저장하고 다시 읽지 않고 계산한 값을 그대로 쓴다.
 *  - 당첨번호가 나중에 바뀌어 다시 검증해야 하는 경우는 관리자 재검증(API-25)에서 다룬다.
 *  - 이 조회는 DB 에 쓰기도 하므로 읽기 전용 트랜잭션이 아니다.
 */
@Service
public class IssueResultService {

    /** 발급코드 형식 (IssueCodeGenerator 와 같은 글자 집합, 12자. IssueQueryService 와 같은 규칙) */
    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Z2-9]{12}$");

    private static final String RATE_PURPOSE = "issue-result";

    private final NumberIssueRepository issueRepo;
    private final IssueNumberRepository numberRepo;
    private final DrawRoundRepository drawRepo;
    private final WinningVerificationRepository verificationRepo;
    private final RequestRateLimiter rateLimiter;
    private final RateLimitProperties rateProps;

    public IssueResultService(NumberIssueRepository issueRepo,
                              IssueNumberRepository numberRepo,
                              DrawRoundRepository drawRepo,
                              WinningVerificationRepository verificationRepo,
                              RequestRateLimiter rateLimiter,
                              RateLimitProperties rateProps) {
        this.issueRepo = issueRepo;
        this.numberRepo = numberRepo;
        this.drawRepo = drawRepo;
        this.verificationRepo = verificationRepo;
        this.rateLimiter = rateLimiter;
        this.rateProps = rateProps;
    }

    /** clientKey 는 접속지(IP) 구분값 */
    @Transactional
    public IssueResultResponse getResult(String issueCode, String clientKey) {
        rateLimiter.check(RATE_PURPOSE, clientKey, rateProps.issueResultPerMinute());   // 없는 코드를 물어도 횟수에 포함

        String code = issueCode == null ? "" : issueCode.trim().toUpperCase();
        NumberIssue issue = CODE_FORMAT.matcher(code).matches() ? issueRepo.findByIssueCode(code).orElse(null) : null;
        if (issue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "발급 내역을 찾을 수 없어요. 발급코드를 확인해 주세요.",
                    "발급코드 없음/형식 오류: length=" + (issueCode == null ? 0 : issueCode.length()));
        }

        List<IssueNumber> games = numberRepo.findByNumberIssueIdOrderByGameOrderAsc(issue.getId());
        DrawRound draw = drawRepo.findByRoundNo(issue.getTargetRoundNo()).orElse(null);
        if (draw == null) {
            return pending(issue, games);
        }
        return verified(issue, games, draw);
    }

    /** 추첨 전: 번호만 보여주고 결과는 비워 둔다. */
    private IssueResultResponse pending(NumberIssue issue, List<IssueNumber> games) {
        List<IssueResultResponse.Game> list = games.stream()
                .map(g -> new IssueResultResponse.Game(g.getGameOrder(), g.numbers(), null, null, null, null, null))
                .toList();
        return new IssueResultResponse(issue.getIssueCode(), issue.getTargetRoundNo(), false, null, null,
                VerificationStatus.PENDING.name(), VerificationStatus.PENDING.label(), null, null, list);
    }

    /** 추첨 후: 저장된 결과는 그대로 쓰고, 없는 게임만 계산해서 저장한다. */
    private IssueResultResponse verified(NumberIssue issue, List<IssueNumber> games, DrawRound draw) {
        List<Integer> winning = draw.getNumbers();
        int bonus = draw.getBonusNumber();

        Map<Long, MatchResult> saved = new HashMap<>();
        if (!games.isEmpty()) {
            List<Long> ids = games.stream().map(IssueNumber::getId).toList();
            for (WinningVerification v : verificationRepo.findByIssueNumberIdIn(ids)) {
                saved.put(v.getIssueNumberId(), v.toMatchResult());
            }
        }

        List<IssueResultResponse.Game> list = new ArrayList<>();
        Integer bestRank = null;
        for (IssueNumber g : games) {
            MatchResult r = saved.get(g.getId());
            if (r == null) {
                r = WinningRankRule.evaluate(g.numbers(), winning, bonus);
                verificationRepo.insertIfAbsent(g.getId(), draw.getRoundNo(), r.matchCount(), r.bonusMatched(),
                        r.rank(), VerificationResultStatus.VERIFIED.name());
            }
            if (r.rank() != null && (bestRank == null || r.rank() < bestRank)) {
                bestRank = r.rank();
            }
            List<Integer> matched = g.numbers().stream().filter(winning::contains).toList();
            list.add(new IssueResultResponse.Game(g.getGameOrder(), g.numbers(), r.matchCount(), r.bonusMatched(),
                    r.rank(), rankLabel(r.rank()), matched));
        }

        return new IssueResultResponse(issue.getIssueCode(), issue.getTargetRoundNo(), true, winning, bonus,
                VerificationStatus.VERIFIED.name(), VerificationStatus.VERIFIED.label(),
                bestRank, rankLabel(bestRank), list);
    }

    /** 등수 → 화면 표시 문구. null 이면 낙첨 */
    private static String rankLabel(Integer rank) {
        return rank == null ? "낙첨" : rank + "등";
    }
}
