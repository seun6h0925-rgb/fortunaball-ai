package com.fortunaball.issue.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.domain.VerificationStatus;
import com.fortunaball.issue.dto.IssueDetailResponse;
import com.fortunaball.issue.dto.IssueListResponse;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.IssueNumberRepository.VerificationCount;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.stat.domain.PatternCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 발급 내역 조회 (API-05 상세, API-42 목록). 읽기 전용이라 DB 를 바꾸지 않는다.
 * 📚 배운 내용: @Service, @Transactional(readOnly = true)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 상세(API-05)는 "발급코드를 아는 사람"이면 볼 수 있다. (게스트 토큰을 요구하지 않는다 — API 명세서 기준)
 *    그 대신 코드를 무작위로 대입하는 공격을 막으려고 접속지(IP)별 조회 횟수를 제한한다. (429)
 *  - 형식이 틀린 코드와 없는 코드는 똑같이 404 로 답한다. (어떤 코드가 존재하는지 알아내는 단서를 주지 않으려고)
 *  - 목록(API-42)은 게스트 토큰으로 "이 기기에서 발급한 것"만 보여준다.
 *  - 검증 상태는 저장하지 않고 winning_verification 행 수로 계산한다. 당첨 검증 기능 전에는 모두 "검증 대기".
 */
@Service
public class IssueQueryService {

    /** 발급코드 형식 (IssueCodeGenerator 와 같은 글자 집합, 12자) */
    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Z2-9]{12}$");

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private static final String RATE_PURPOSE = "issue-lookup";

    private final NumberIssueRepository issueRepo;
    private final IssueNumberRepository numberRepo;
    private final RequestRateLimiter rateLimiter;
    private final RateLimitProperties rateProps;

    public IssueQueryService(NumberIssueRepository issueRepo,
                             IssueNumberRepository numberRepo,
                             RequestRateLimiter rateLimiter,
                             RateLimitProperties rateProps) {
        this.issueRepo = issueRepo;
        this.numberRepo = numberRepo;
        this.rateLimiter = rateLimiter;
        this.rateProps = rateProps;
    }

    /** API-05: 발급코드로 상세 조회. clientKey 는 접속지(IP) 구분값 */
    @Transactional(readOnly = true)
    public IssueDetailResponse getDetail(String issueCode, String clientKey) {
        rateLimiter.check(RATE_PURPOSE, clientKey, rateProps.issueLookupPerMinute());   // 없는 코드를 물어도 횟수에 포함

        String code = issueCode == null ? "" : issueCode.trim().toUpperCase();
        NumberIssue issue = CODE_FORMAT.matcher(code).matches() ? issueRepo.findByIssueCode(code).orElse(null) : null;
        if (issue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "발급 내역을 찾을 수 없어요. 발급코드를 확인해 주세요.",
                    "발급코드 없음/형식 오류: length=" + (issueCode == null ? 0 : issueCode.length()));
        }

        List<IssueDetailResponse.Game> games = numberRepo.findByNumberIssueIdOrderByGameOrderAsc(issue.getId()).stream()
                .map((IssueNumber n) -> new IssueDetailResponse.Game(n.getGameOrder(), n.numbers()))
                .toList();
        VerificationStatus status = statusOf(issue.getId(), verificationOf(List.of(issue.getId())));

        return new IssueDetailResponse(
                issue.getIssueCode(), issue.getTargetRoundNo(), issue.getCreatedAt(), issue.getGameCount(),
                patternCodeOf(issue), patternNameOf(issue), games, status.name(), status.label());
    }

    /** API-42: 이 기기의 발급 내역 목록. page 는 0부터, size 는 1~MAX_PAGE_SIZE */
    @Transactional(readOnly = true)
    public IssueListResponse getList(GuestToken guest, String pageRaw, String sizeRaw) {
        int page = parseInt(pageRaw, 0, "page", 0, Integer.MAX_VALUE);
        int size = parseInt(sizeRaw, DEFAULT_PAGE_SIZE, "size", 1, MAX_PAGE_SIZE);

        Page<NumberIssue> result = issueRepo.findByGuestTokenOrderByCreatedAtDescIdDesc(
                guest.value(), PageRequest.of(page, size));

        List<Long> ids = result.getContent().stream().map(NumberIssue::getId).toList();
        Map<Long, VerificationCount> counts = ids.isEmpty() ? Map.of() : verificationOf(ids);
        Map<Long, List<Integer>> firstGames = ids.isEmpty() ? Map.of() : firstGamesOf(ids);

        List<IssueListResponse.Item> items = result.getContent().stream().map(i -> {
            VerificationStatus status = statusOf(i.getId(), counts);
            return new IssueListResponse.Item(i.getIssueCode(), i.getTargetRoundNo(), i.getGameCount(),
                    i.getCreatedAt(), patternCodeOf(i), patternNameOf(i), status.name(), status.label(),
                    firstGames.get(i.getId()));
        }).toList();

        return new IssueListResponse(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    /** 발급 건별 첫 번째 게임 번호 (목록 카드용). 첫 게임이 없는 건은 맵에 없어 null 로 나간다 */
    private Map<Long, List<Integer>> firstGamesOf(List<Long> issueIds) {
        Map<Long, List<Integer>> map = new HashMap<>();
        for (IssueNumber n : numberRepo.findByNumberIssueIdInAndGameOrder(issueIds, 1)) {
            map.put(n.getNumberIssueId(), n.numbers());
        }
        return map;
    }

    private Map<Long, VerificationCount> verificationOf(List<Long> issueIds) {
        Map<Long, VerificationCount> map = new HashMap<>();
        for (VerificationCount c : numberRepo.countVerification(issueIds)) {
            map.put(c.getIssueId(), c);
        }
        return map;
    }

    private static VerificationStatus statusOf(Long issueId, Map<Long, VerificationCount> counts) {
        VerificationCount c = counts.get(issueId);
        if (c == null) {
            return VerificationStatus.PENDING;
        }
        return VerificationStatus.of(c.getTotalGames(), c.getVerifiedGames(), c.getErrorGames());
    }

    private static String patternCodeOf(NumberIssue issue) {
        return patternOf(issue) == null ? null : patternOf(issue).name();
    }

    private static String patternNameOf(NumberIssue issue) {
        return patternOf(issue) == null ? null : patternOf(issue).displayName();
    }

    /** 패턴을 골라 만든 발급이면 그 패턴, 아니면 null (알 수 없는 값이 저장돼 있어도 오류 없이 null) */
    private static PatternCode patternOf(NumberIssue issue) {
        if (!"STAT_PATTERN".equals(issue.getGenerationBasisType()) || issue.getGenerationBasisValue() == null) {
            return null;
        }
        try {
            return PatternCode.valueOf(issue.getGenerationBasisValue());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String raw, int defaultValue, String name, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw invalid(name, raw, min, max);
        }
        if (value < min || value > max) {
            throw invalid(name, raw, min, max);
        }
        return value;
    }

    private static BusinessException invalid(String name, String raw, int min, int max) {
        return new BusinessException(ErrorCode.INVALID_INPUT,
                name + " 값이 올바르지 않아요.",
                name + "=" + raw + " (허용: " + min + "~" + max + ")");
    }
}
