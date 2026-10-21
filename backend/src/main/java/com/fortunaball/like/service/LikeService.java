package com.fortunaball.like.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.like.dto.LikeStatusResponse;
import com.fortunaball.like.repository.LikeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 서비스 좋아요(엄지 버튼). 모든 방문자가 같은 전체 수를 보고, 한 사람이 하루에 누를 수 있는 횟수는 제한한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - "한 사람"은 비회원 토큰(X-Guest-Token)으로 구분한다. 토큰은 바꿀 수 있어서, 같은 접속지(IP)에도 하루 상한을 따로 둔다.
 *  - 하루의 기준은 한국 시간 0시다. 다음 날이 되면 횟수가 새로 시작한다.
 *  - 토큰·IP 원문은 저장하지 않고 SHA-256 해시만 저장한다. (로그에도 남기지 않는다)
 *  - 한도에 닿으면 429(TOO_MANY_REQUESTS)와 안내 문구를 돌려준다. 전체 수는 올라가지 않는다.
 *  - 설정: fortuna.like.daily-limit-per-guest(기본 5), fortuna.like.daily-limit-per-ip(기본 30)
 */
@Service
public class LikeService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int KEEP_DAYS = 7;

    private final LikeRepository repo;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int guestLimit;
    private final int ipLimit;

    @Autowired
    public LikeService(LikeRepository repo, TransactionTemplate tx,
                       @Value("${fortuna.like.daily-limit-per-guest:5}") int guestLimit,
                       @Value("${fortuna.like.daily-limit-per-ip:30}") int ipLimit) {
        this(repo, tx, Clock.system(ZONE), guestLimit, ipLimit);
    }

    /** 테스트용: 시계를 직접 넣는다 */
    LikeService(LikeRepository repo, TransactionTemplate tx, Clock clock, int guestLimit, int ipLimit) {
        this.repo = repo;
        this.tx = tx;
        this.clock = clock;
        this.guestLimit = Math.max(1, guestLimit);
        this.ipLimit = Math.max(1, ipLimit);
    }

    /** 현재 전체 수와 이 사람의 오늘 횟수 */
    public LikeStatusResponse status(String guestToken) {
        LocalDate today = today();
        return build(repo.total(), repo.todayCount(today, guestKey(guestToken)));
    }

    /** 좋아요 1회. 한도 안이면 전체 수를 올리고 현황을 돌려준다. */
    public LikeStatusResponse press(String guestToken, String clientIp) {
        LocalDate today = today();
        String gk = guestKey(guestToken);
        String ik = "I:" + sha256(clientIp == null ? "unknown" : clientIp);
        return Objects.requireNonNull(tx.execute(status -> {
            if (!repo.tryIncrement(today, gk, guestLimit)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "오늘은 좋아요를 " + guestLimit + "번 모두 눌렀어요. 내일 또 눌러 주세요!",
                        "좋아요 사용자 한도 초과: limit=" + guestLimit);
            }
            if (!repo.tryIncrement(today, ik, ipLimit)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "이 접속지에서는 오늘 더 이상 좋아요를 누를 수 없어요. 내일 다시 눌러 주세요!",
                        "좋아요 접속지 한도 초과: limit=" + ipLimit);
            }
            long total = repo.incrementTotal();
            return build(total, repo.todayCount(today, gk));
        }));
    }

    /** 오래된 하루 기록 정리. 매일 한국 시간 00:10 */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void purgeOld() {
        repo.deleteBefore(today().minusDays(KEEP_DAYS));
    }

    private LikeStatusResponse build(long total, int todayCount) {
        return new LikeStatusResponse(total, todayCount, guestLimit, Math.max(0, guestLimit - todayCount));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String guestKey(String guestToken) {
        return "G:" + sha256(guestToken);
    }

    static String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
