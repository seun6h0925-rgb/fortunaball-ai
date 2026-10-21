package com.fortunaball.admin.action.service;

import com.fortunaball.admin.action.dto.JobRunResult;
import com.fortunaball.admin.action.dto.RecalculateResult;
import com.fortunaball.admin.action.dto.ResyncResult;
import com.fortunaball.admin.action.dto.VerifyResult;
import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.collector.DrawCollector;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.dto.CollectSummary;
import com.fortunaball.draw.dto.DrawApiResponse;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.draw.service.DrawCacheEvictor;
import com.fortunaball.draw.service.DrawCollectService;
import com.fortunaball.draw.service.SchedulerLogRecorder;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.service.StatSnapshotService;
import com.fortunaball.stat.service.StatSnapshotTrigger;
import com.fortunaball.verify.service.VerificationBatchService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자가 직접 실행하는 운영 작업 4가지.
 *  - API-23 회차 재수집: 공식 API 에서 한 회차를 다시 받아 DB 와 비교하고, 다르면 공식 값으로 덮어쓴다.
 *  - API-25 당첨 검증 재실행: 아직 판정이 없는 발급 게임을 판정한다. (이미 저장된 판정은 바뀌지 않는다)
 *  - API-40 스케줄러 수동 실행: DRAW_COLLECT / VERIFY_BATCH / STAT_SNAPSHOT 중 하나를 지금 실행한다.
 *  - API-47 통계 재계산: 한 회차의 통계 카드를 지금 저장된 회차 데이터로 다시 만든다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 회차 값은 "사람이 입력·수정"하지 않는다. 재수집은 오직 공식 API 에서 받은 값만 저장한다. (기획 원칙)
 *  - 모든 실행은 admin_action_log 에 남긴다. 재수집은 바뀐 값의 "전 → 후"를 적는다.
 *  - 수동 실행은 scheduler_log 에도 남는다. 작업 이름 끝에 _MANUAL 이 붙어서 자동 실행과 구분된다.
 *  - 같은 작업이 이미 돌고 있으면 또 시작하지 않고 SKIPPED_RUNNING / ALREADY_RUNNING 으로 알려 준다.
 *  - 이미 판정된 게임의 등수는 회차 값이 바뀌어도 자동으로 고쳐지지 않는다. (판정은 한 번 저장되면 바뀌지 않는 규칙)
 */
@Service
public class AdminOperationService {

    private static final Logger log = LoggerFactory.getLogger(AdminOperationService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_ROUND = 5000;
    private static final String SOURCE = "DHLOTTERY_API";

    public static final String JOB_DRAW = "DRAW_COLLECT";
    public static final String JOB_VERIFY = "VERIFY_BATCH";
    public static final String JOB_STAT = "STAT_SNAPSHOT";
    static final String LOG_DRAW = "DRAW_COLLECT_MANUAL";
    static final String LOG_VERIFY = "VERIFY_BATCH_MANUAL";
    static final String LOG_STAT = "STAT_SNAPSHOT_MANUAL";
    private static final String COLLECT_COMPLETE = "마지막 회차까지 확인 완료";

    private final DrawCollector collector;
    private final DrawRoundRepository roundRepo;
    private final DrawPrizeRepository prizeRepo;
    private final TransactionTemplate tx;
    private final DrawCacheEvictor cacheEvictor;
    private final DrawCollectService collectService;
    private final VerificationBatchService verifyBatch;
    private final StatSnapshotService statService;
    private final StatSnapshotTrigger statTrigger;
    private final SchedulerLogRecorder logRecorder;
    private final AdminActionRecorder recorder;

    private final AtomicBoolean verifyRunning = new AtomicBoolean(false);
    private final AtomicBoolean statRunning = new AtomicBoolean(false);

    public AdminOperationService(DrawCollector collector,
                                 DrawRoundRepository roundRepo,
                                 DrawPrizeRepository prizeRepo,
                                 TransactionTemplate tx,
                                 DrawCacheEvictor cacheEvictor,
                                 DrawCollectService collectService,
                                 VerificationBatchService verifyBatch,
                                 StatSnapshotService statService,
                                 StatSnapshotTrigger statTrigger,
                                 SchedulerLogRecorder logRecorder,
                                 AdminActionRecorder recorder) {
        this.collector = collector;
        this.roundRepo = roundRepo;
        this.prizeRepo = prizeRepo;
        this.tx = tx;
        this.cacheEvictor = cacheEvictor;
        this.collectService = collectService;
        this.verifyBatch = verifyBatch;
        this.statService = statService;
        this.statTrigger = statTrigger;
        this.logRecorder = logRecorder;
        this.recorder = recorder;
    }

    // ------------------------------------------------------------------ API-23 회차 재수집

    private record Applied(Long roundId, String result, String changes) {
    }

    public ResyncResult resync(int roundNo) {
        AdminPrincipal admin = currentAdmin();
        checkRound(roundNo);

        // 공식 사이트에서 받는다. 통신 실패·점검은 DrawCollector 가 BusinessException 으로 던진다.
        DrawApiResponse.Item item = collector.fetch(roundNo).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, "공식 사이트에 아직 이 회차 결과가 없어요.", "재수집 대상 회차 결과 없음: " + roundNo));
        if (!item.isPrizeAggregated()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "아직 2~5등 당첨금 집계 전이라 재수집할 수 없어요. 집계가 끝난 뒤 다시 시도해 주세요.",
                    "재수집 대상 회차의 2~5등 집계 전: " + roundNo);
        }
        DrawRound fresh = item.toDrawRound(SOURCE);
        List<DrawPrize> prizes = new ArrayList<>();
        for (DrawPrize p : item.toDrawPrizes()) {
            prizes.add(p);
        }
        String problem = checkPrizes(prizes);
        if (problem != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "공식 사이트에서 받은 당첨금 정보가 올바르지 않아 저장하지 않았어요. (" + problem + ")",
                    "재수집 1~5등 검증 실패: " + roundNo + " / " + problem);
        }

        Applied applied = Objects.requireNonNull(tx.execute(status -> apply(roundNo, fresh, prizes)));

        String statNote = "";
        if (!"UNCHANGED".equals(applied.result())) {
            cacheEvictor.evictRound(roundNo);   // 저장이 확정된 뒤에 캐시를 비운다
            statNote = refreshStats(roundNo, applied.result());
        }
        recorder.record(admin.id(), "RESYNC_DRAW", "draw_round", applied.roundId(),
                roundNo + "회 재수집 " + applied.result() + ": " + applied.changes() + statNote);

        List<ResyncResult.PrizeLine> lines = new ArrayList<>();
        for (DrawPrize p : prizes) {
            lines.add(new ResyncResult.PrizeLine(p.getWinningRank(), p.getPrizeAmount(), p.getWinnerCount()));
        }
        lines.sort((a, b) -> Integer.compare(a.rank(), b.rank()));
        return new ResyncResult(roundNo, applied.result(), applied.changes(), true, "1~5등 모두 확인됨", lines);
    }

    /** 트랜잭션 안에서 저장·비교·덮어쓰기를 한꺼번에 한다. */
    private Applied apply(int roundNo, DrawRound fresh, List<DrawPrize> prizes) {
        Optional<DrawRound> existingOpt = roundRepo.findByRoundNo(roundNo);
        if (existingOpt.isEmpty()) {
            DrawRound saved = roundRepo.save(fresh);
            prizeRepo.saveAll(prizes);
            return new Applied(saved.getId(), "CREATED", "저장돼 있지 않던 회차를 새로 저장");
        }
        DrawRound existing = existingOpt.get();
        List<String> diffs = new ArrayList<>();
        if (!existing.getNumbers().equals(fresh.getNumbers())) {
            diffs.add("당첨번호 " + existing.getNumbers() + " → " + fresh.getNumbers());
        }
        if (existing.getBonusNumber() != fresh.getBonusNumber()) {
            diffs.add("보너스 " + existing.getBonusNumber() + " → " + fresh.getBonusNumber());
        }
        if (!Objects.equals(existing.getDrawDate(), fresh.getDrawDate())) {
            diffs.add("추첨일 " + existing.getDrawDate() + " → " + fresh.getDrawDate());
        }
        Map<Integer, DrawPrize> old = new TreeMap<>();
        for (DrawPrize p : prizeRepo.findByRoundNoOrderByWinningRankAsc(roundNo)) {
            old.put(p.getWinningRank(), p);
        }
        for (DrawPrize np : prizes) {
            DrawPrize op = old.get(np.getWinningRank());
            if (op == null) {
                prizeRepo.save(np);
                diffs.add(np.getWinningRank() + "등 정보 새로 저장");
            } else if (op.getPrizeAmount() != np.getPrizeAmount() || op.getWinnerCount() != np.getWinnerCount()) {
                diffs.add(np.getWinningRank() + "등 당첨금 " + op.getPrizeAmount() + "원/" + op.getWinnerCount() + "명 → "
                        + np.getPrizeAmount() + "원/" + np.getWinnerCount() + "명");
                op.applyOfficial(np);
            }
        }
        if (diffs.isEmpty()) {
            return new Applied(existing.getId(), "UNCHANGED", "공식 사이트 값과 같아서 바꾸지 않음");
        }
        existing.applyOfficial(fresh);
        return new Applied(existing.getId(), "UPDATED", String.join(" / ", diffs));
    }

    /** 1~5등이 정확히 한 줄씩 있는지 확인한다. 문제가 없으면 null. */
    static String checkPrizes(List<DrawPrize> prizes) {
        boolean[] seen = new boolean[6];
        for (DrawPrize p : prizes) {
            int r = p.getWinningRank();
            if (r < 1 || r > 5 || seen[r]) {
                return "등수 중복 또는 범위 오류: " + r;
            }
            seen[r] = true;
        }
        for (int r = 1; r <= 5; r++) {
            if (!seen[r]) {
                return r + "등 정보 없음";
            }
        }
        return null;
    }

    /** 회차가 바뀐 뒤 통계를 맞춘다. 실패해도 재수집 결과에는 영향이 없고, 기록에 메모만 남긴다. */
    private String refreshStats(int roundNo, String result) {
        try {
            if ("CREATED".equals(result)) {
                statTrigger.refresh("관리자 회차 재수집 (" + roundNo + "회)");
                return " / 통계 맞춤 실행";
            }
            int n = statService.recalculateAffectedBy(roundNo, LocalDateTime.now(KST));
            return " / 영향받는 통계 " + n + "건 재계산";
        } catch (RuntimeException e) {
            log.warn("[관리자재수집] 통계 갱신 실패 (회차 저장은 완료됨): {}", e.toString());
            return " / 통계 갱신 실패(통계 재계산 API 로 다시 시도 필요)";
        }
    }

    // ------------------------------------------------------------------ API-25 당첨 검증 재실행

    public VerifyResult verify() {
        AdminPrincipal admin = currentAdmin();
        VerifyResult result = doVerify();
        recorder.record(admin.id(), "RUN_VERIFY", "winning_verification", null,
                "당첨 검증 재실행: " + result.status() + ", 새로 판정 " + result.verified() + "건, 실패 " + result.failed() + "건");
        return result;
    }

    private VerifyResult doVerify() {
        if (!verifyRunning.compareAndSet(false, true)) {
            return new VerifyResult("ALREADY_RUNNING", 0, 0, "이미 검증이 실행 중이에요. 잠시 뒤 다시 시도해 주세요.");
        }
        try {
            if (!verifyBatch.hasPending()) {
                return new VerifyResult("NOTHING_TO_DO", 0, 0, "새로 판정할 게임이 없어요.");
            }
            Long logId = logRecorder.start(LOG_VERIFY, null);
            try {
                VerificationBatchService.Result r = verifyBatch.verifyAll();
                if (r.failed() == 0) {
                    logRecorder.success(logId, r.verified());
                } else {
                    logRecorder.failed(logId, r.verified(), "판정 실패 " + r.failed() + "건");
                }
                return new VerifyResult("DONE", r.verified(), r.failed(),
                        "새로 판정 " + r.verified() + "건, 실패 " + r.failed() + "건");
            } catch (RuntimeException e) {
                logRecorder.failed(logId, 0, "예기치 못한 오류: " + e);
                throw e;
            }
        } finally {
            verifyRunning.set(false);
        }
    }

    // ------------------------------------------------------------------ API-40 스케줄러 수동 실행

    public JobRunResult runJob(String rawJobName) {
        AdminPrincipal admin = currentAdmin();
        String jobName = rawJobName == null ? "" : rawJobName.trim().toUpperCase(Locale.ROOT);
        JobRunResult result = switch (jobName) {
            case JOB_DRAW -> runDraw();
            case JOB_VERIFY -> {
                VerifyResult v = doVerify();
                int processed = v.verified();
                String status = switch (v.status()) {
                    case "DONE" -> v.failed() == 0 ? "SUCCESS" : "FAILED";
                    case "ALREADY_RUNNING" -> "SKIPPED_RUNNING";
                    default -> "NOTHING_TO_DO";
                };
                yield new JobRunResult(JOB_VERIFY, status, processed, v.message());
            }
            case JOB_STAT -> runStat();
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "실행할 수 있는 작업은 " + JOB_DRAW + ", " + JOB_VERIFY + ", " + JOB_STAT + " 입니다.",
                    "알 수 없는 작업 이름: " + rawJobName);
        };
        recorder.record(admin.id(), "RUN_JOB", "scheduler_log", null,
                jobName + " 수동 실행: " + result.status() + ", 처리 " + result.processedCount() + "건 / " + result.message());
        return result;
    }

    private JobRunResult runDraw() {
        CollectSummary s = collectService.collectAll(LOG_DRAW);
        String reason = s.stopReason();
        if (reason != null && reason.contains("이미 실행 중")) {
            return new JobRunResult(JOB_DRAW, "SKIPPED_RUNNING", 0, "이미 회차 수집이 실행 중이에요.");
        }
        boolean ok = s.failedRounds().isEmpty() && COLLECT_COMPLETE.equals(reason);
        String message = "새로 저장 " + s.saved() + "건 / " + reason
                + (s.failedRounds().isEmpty() ? "" : " / 실패 회차 " + s.failedRounds());
        return new JobRunResult(JOB_DRAW, ok ? "SUCCESS" : "FAILED", s.saved(), message);
    }

    private JobRunResult runStat() {
        if (!statRunning.compareAndSet(false, true)) {
            return new JobRunResult(JOB_STAT, "SKIPPED_RUNNING", 0, "이미 통계 맞춤이 실행 중이에요.");
        }
        try {
            Long logId = logRecorder.start(LOG_STAT, null);
            boolean ok = statTrigger.refresh("관리자 수동 실행");
            if (ok) {
                logRecorder.success(logId, 0);
                return new JobRunResult(JOB_STAT, "SUCCESS", 0, "통계를 지금 상황에 맞췄어요.");
            }
            logRecorder.failed(logId, 0, "통계 맞춤 중 오류 (서버 로그 확인)");
            return new JobRunResult(JOB_STAT, "FAILED", 0, "통계 맞춤 중 오류가 났어요. 서버 로그를 확인해 주세요.");
        } finally {
            statRunning.set(false);
        }
    }

    // ------------------------------------------------------------------ API-47 통계 재계산

    public RecalculateResult recalculate(int roundNo) {
        AdminPrincipal admin = currentAdmin();
        checkRound(roundNo);
        StatRoundSnapshot snap = statService.recalculate(roundNo, LocalDateTime.now(KST))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "이 회차의 통계가 없어요. (통계는 최근 저장 회차와 이번 대상 회차에만 있어요)",
                        "재계산 대상 통계 없음: " + roundNo));
        recorder.record(admin.id(), "RECALCULATE_STAT", "stat_round_snapshot", snap.getId(),
                roundNo + "회 통계 재계산 (기준 " + snap.getBasisRoundNo() + "회, 상태 " + snap.getStatus() + ")");
        return new RecalculateResult(roundNo, snap.getBasisRoundNo(), snap.getStatus().name(), snap.getGeneratedAt());
    }

    // ------------------------------------------------------------------ 보조

    private static void checkRound(int roundNo) {
        if (roundNo < 1 || roundNo > MAX_ROUND) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회차 번호는 1 이상 " + MAX_ROUND + " 이하로 입력해 주세요.",
                    "회차 번호 범위 오류: " + roundNo);
        }
    }

    /** 관리자 번호는 로그인 세션에서만 가져온다. */
    private static AdminPrincipal currentAdmin() {
        return AdminAccess.current().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요.", "관리자 세션 없음"));
    }
}
