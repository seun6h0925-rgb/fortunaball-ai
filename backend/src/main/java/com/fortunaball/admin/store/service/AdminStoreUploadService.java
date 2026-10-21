package com.fortunaball.admin.store.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.admin.store.dto.DiffSummary;
import com.fortunaball.admin.store.dto.StoreDiffItem;
import com.fortunaball.admin.store.dto.StoreUploadApplyResult;
import com.fortunaball.admin.store.dto.StoreUploadDetail;
import com.fortunaball.admin.store.dto.StoreUploadResult;
import com.fortunaball.admin.store.dto.StoreUploadRow;
import com.fortunaball.admin.store.dto.UploadValidation;
import com.fortunaball.admin.store.repository.StoreUploadRepository;
import com.fortunaball.admin.store.service.StoreDiffCalculator.Diff;
import com.fortunaball.admin.store.service.StoreDiffCalculator.ExistingStore;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.store.importer.StoreCsvParser;
import com.fortunaball.store.importer.StoreCsvRow;
import com.fortunaball.store.importer.StoreImportService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매점 파일 업로드 (API-30 업로드, 49 비교 결과, 50 반영, 51 취소, 52 이력).
 *
 * 흐름: 업로드(STAGED, DB 는 아직 그대로) → 변경점 확인 → 반영(APPLIED) 또는 취소(CANCELED).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 지원하는 파일 종류는 지금 FIRST_SECOND_CSV (회차별 1·2등 당첨 판매점 CSV, 열은 StoreCsvParser 참고) 하나다.
 *    공공데이터 판매점 CSV(STORE_LIST)와 동행복권 엑셀은 실제 샘플 파일의 열 구성을 본 뒤에 파서를 추가한다.
 *  - 업로드 때 파일 전체를 검증한다(형식·열·값·회차 존재). 통과하면 STAGED 로 저장하고 파일을 서버 폴더에 보관한다.
 *    검증에 실패하면 이력도 만들지 않고 오류만 돌려준다.
 *  - 반영은 파일을 다시 읽어 StoreImportService.importRows 로 넣는다. (처음 CSV 를 넣던 것과 같은 코드라 결과가 같다)
 *    판매점·집계는 덮어쓰고, 파일 회차 범위의 당첨 이력은 지우고 다시 넣는다. 전체가 한 트랜잭션이라 중간에 실패하면 아무것도 바뀌지 않는다.
 *  - 반영·취소는 업로드 행을 잠그고(FOR UPDATE) 상태가 STAGED 일 때만 한다. 같은 업로드를 두 번 반영·취소할 수 없다.
 *  - 오래된 파일을 나중에 반영하면 더 새로운 값을 덮어쓸 수 있다. 반영 전에 변경점(API-49)을 꼭 확인한다.
 *  - 모든 업로드·반영·취소는 admin_action_log 에 남긴다.
 */
@Service
public class AdminStoreUploadService {

    private static final Logger log = LoggerFactory.getLogger(AdminStoreUploadService.class);

    public static final String TYPE_FIRST_SECOND_CSV = "FIRST_SECOND_CSV";
    private static final Set<String> UNSUPPORTED_TYPES = Set.of("STORE_LIST", "FIRST_AUTO", "FIRST_SECOND_EXCEL");
    static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    static final int DEFAULT_ITEM_SIZE = 50;
    static final int MAX_ITEM_SIZE = 200;
    private static final String TABLE = "store_data_upload";
    private static final Set<String> STATUSES = Set.of("STAGED", "APPLIED", "FAILED", "CANCELED");
    private static final int HISTORY_DEFAULT_DAYS = 365;

    private final StoreUploadRepository repo;
    private final StoreUploadFiles files;
    private final StoreImportService importService;
    private final AdminActionRecorder recorder;

    public AdminStoreUploadService(StoreUploadRepository repo, StoreUploadFiles files,
                                   StoreImportService importService, AdminActionRecorder recorder) {
        this.repo = repo;
        this.files = files;
        this.importService = importService;
        this.recorder = recorder;
    }

    // ------------------------------------------------------------------ API-30 업로드

    public StoreUploadResult upload(String originalFileName, byte[] bytes, String rawType) {
        AdminPrincipal admin = currentAdmin();
        String type = checkType(rawType);
        String fileName = checkFile(originalFileName, bytes);

        List<StoreCsvRow> rows = parse(bytes);
        int minRound = rows.stream().mapToInt(StoreCsvRow::round).min().orElseThrow();
        int maxRound = rows.stream().mapToInt(StoreCsvRow::round).max().orElseThrow();
        checkRoundsExist(rows, minRound, maxRound);

        Diff diff = StoreDiffCalculator.compute(rows, repo.loadExistingStores(),
                repo.loadHistoryCounts(minRound, maxRound));
        LocalDate baseDate = repo.drawDate(maxRound);
        int storeCount = diff.summary().newStores() + diff.summary().changedStores() + diff.summary().unchangedStores();

        long uploadId = repo.insertUpload(type, fileName, baseDate, rows.size(), diff.summary().newStores(),
                diff.summary().changedStores(), "STAGED", admin.id());
        try {
            files.save(uploadId, bytes);
        } catch (IOException e) {
            repo.markFailed(uploadId, "파일 보관 실패: " + e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일을 서버에 보관하지 못했어요. 다시 시도해 주세요.",
                    "판매점 업로드 파일 저장 실패: " + e);
        }
        recorder.record(admin.id(), "UPLOAD_STORE_FILE", TABLE, uploadId, fileName + " 업로드(STAGED): 총 " + rows.size()
                + "줄, 회차 " + minRound + "~" + maxRound + ", 신규 " + diff.summary().newStores()
                + ", 변경 " + diff.summary().changedStores());
        return new StoreUploadResult(uploadId, type, fileName, "STAGED",
                new UploadValidation(rows.size(), storeCount, minRound, maxRound, baseDate), diff.summary());
    }

    // ------------------------------------------------------------------ API-49 비교 결과

    @Transactional(readOnly = true)
    public StoreUploadDetail detail(long uploadId, String rawKind, Integer page, Integer size) {
        StoreUploadRow up = findOrThrow(uploadId);
        String kind = checkKind(rawKind);
        int p = AdminQuery.page(page);
        int s = size == null || size < 1 ? DEFAULT_ITEM_SIZE : Math.min(size, MAX_ITEM_SIZE);

        if (!"STAGED".equals(up.status())) {
            // 이미 끝난 업로드는 지금 DB 와 비교하면 의미가 없다. 업로드 때 센 신규·변경 건수만 보여 준다.
            DiffSummary summary = new DiffSummary(up.newRows(), up.changedRows(), null, 0, null, null);
            return new StoreUploadDetail(up, summary, new AdminPage<>(List.of(), p, s, 0, false));
        }
        List<StoreCsvRow> rows = parse(readFile(uploadId));
        int minRound = rows.stream().mapToInt(StoreCsvRow::round).min().orElseThrow();
        int maxRound = rows.stream().mapToInt(StoreCsvRow::round).max().orElseThrow();
        Diff diff = StoreDiffCalculator.compute(rows, repo.loadExistingStores(),
                repo.loadHistoryCounts(minRound, maxRound));

        List<StoreDiffItem> filtered = new ArrayList<>();
        for (StoreDiffItem item : diff.items()) {
            if (kind == null || kind.equals(item.kind())) {
                filtered.add(item);
            }
        }
        int from = Math.min(filtered.size(), p * s);
        int to = Math.min(filtered.size(), from + s);
        AdminPage<StoreDiffItem> items = new AdminPage<>(new ArrayList<>(filtered.subList(from, to)), p, s,
                filtered.size(), to < filtered.size());
        return new StoreUploadDetail(up, diff.summary(), items);
    }

    // ------------------------------------------------------------------ API-50 반영

    @Transactional
    public StoreUploadApplyResult apply(long uploadId) {
        AdminPrincipal admin = currentAdmin();
        StoreUploadRow up = repo.lockUpload(uploadId).orElseThrow(() -> notFound(uploadId));
        if (!"STAGED".equals(up.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "반영 대기 중인 업로드만 반영할 수 있어요. (현재 상태: "
                    + up.status() + ")", "판매점 업로드 상태 오류: id=" + uploadId + ", status=" + up.status());
        }
        List<StoreCsvRow> rows = parse(readFile(uploadId));

        Map<String, ExistingStore> before = repo.loadExistingStores();
        Set<String> allCodes = new LinkedHashSet<>();
        Set<String> newCodes = new LinkedHashSet<>();
        for (StoreCsvRow r : rows) {
            allCodes.add(r.shopCode());
            if (!before.containsKey(r.shopCode())) {
                newCodes.add(r.shopCode());
            }
        }

        StoreImportService.Result r;
        try {
            r = importService.importRows(rows);
        } catch (IllegalStateException e) {
            // 예: draw_round 에 없는 회차. 던지면 트랜잭션이 취소되어 아무것도 바뀌지 않는다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage(), "판매점 반영 실패: " + e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now(AdminQuery.ZONE);
        repo.stampFirstSeen(uploadId, newCodes, now);
        repo.stampSummaryUpload(uploadId, allCodes);
        repo.markApplied(uploadId, now);
        int geocodePending = repo.countGeocodePending();

        recorder.record(admin.id(), "APPLY_STORE_FILE", TABLE, uploadId, up.fileName() + " 반영: 판매점 " + r.stores()
                + "곳(신규 " + newCodes.size() + ", 폐점 " + r.closedStores() + "), 당첨 이력 " + r.histories()
                + "건, 회차 " + r.minRound() + "~" + r.maxRound());
        log.info("[판매점업로드] 반영 완료: id={}, 판매점 {}곳, 이력 {}건", uploadId, r.stores(), r.histories());
        return new StoreUploadApplyResult(uploadId, "APPLIED", r.stores(), newCodes.size(), r.closedStores(),
                r.histories(), r.summaries(), r.minRound(), r.maxRound(), r.baseDate(), geocodePending);
    }

    // ------------------------------------------------------------------ API-51 취소

    @Transactional
    public StoreUploadRow cancel(long uploadId) {
        AdminPrincipal admin = currentAdmin();
        StoreUploadRow up = repo.lockUpload(uploadId).orElseThrow(() -> notFound(uploadId));
        if (!"STAGED".equals(up.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "반영 대기 중인 업로드만 취소할 수 있어요. (현재 상태: "
                    + up.status() + ")", "판매점 업로드 취소 불가: id=" + uploadId + ", status=" + up.status());
        }
        repo.markCanceled(uploadId);
        files.delete(uploadId);
        recorder.record(admin.id(), "CANCEL_STORE_FILE", TABLE, uploadId, up.fileName() + " 업로드 취소");
        return repo.find(uploadId).orElse(up);
    }

    // ------------------------------------------------------------------ API-52 이력

    @Transactional(readOnly = true)
    public AdminPage<StoreUploadRow> history(LocalDate from, LocalDate to, String rawStatus, Integer page, Integer size) {
        AdminQuery.Range range = AdminQuery.range(from, to, HISTORY_DEFAULT_DAYS);
        String status = null;
        if (rawStatus != null && !rawStatus.isBlank()) {
            status = rawStatus.trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "상태는 STAGED, APPLIED, FAILED, CANCELED 중 하나예요.",
                        "업로드 상태 값 오류: " + rawStatus);
            }
        }
        return repo.history(range.start(), range.endExclusive(), status, AdminQuery.page(page), AdminQuery.size(size));
    }

    // ------------------------------------------------------------------ 보조

    private static String checkType(String raw) {
        String type = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (TYPE_FIRST_SECOND_CSV.equals(type)) {
            return type;
        }
        if (UNSUPPORTED_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이 파일 종류는 아직 지원하지 않아요. 지금은 " + TYPE_FIRST_SECOND_CSV + " 만 올릴 수 있어요.",
                    "미지원 업로드 종류: " + type);
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 종류는 " + TYPE_FIRST_SECOND_CSV + " 를 보내 주세요.",
                "알 수 없는 업로드 종류: " + raw);
    }

    private static String checkFile(String originalName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일을 선택해 주세요.", "빈 파일");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "파일이 너무 커요. 30MB 이하로 올려 주세요.",
                    "판매점 파일 크기 초과: " + bytes.length);
        }
        String name = originalName == null ? "" : originalName.trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일(.csv)만 올릴 수 있어요.",
                    "확장자 오류: " + originalName);
        }
        // 파일명은 이력에 보여 주기만 한다. 길면 자르고 경로 글자는 버린다.
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private static List<StoreCsvRow> parse(byte[] bytes) {
        try {
            List<StoreCsvRow> rows = StoreCsvParser.parseText(new String(bytes, StandardCharsets.UTF_8));
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "파일에 데이터가 없어요.", "판매점 CSV 데이터 없음");
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 내용이 올바르지 않아요. " + e.getMessage(),
                    "판매점 CSV 파싱 실패: " + e.getMessage());
        }
    }

    /** CSV 의 회차가 draw_round 에 모두 있는지 확인한다. 없으면 어느 회차인지 알려 준다. */
    private void checkRoundsExist(List<StoreCsvRow> rows, int minRound, int maxRound) {
        Set<Integer> have = repo.findExistingRounds(minRound, maxRound);
        Set<Integer> missing = new TreeSet<>();
        for (StoreCsvRow r : rows) {
            if (!have.contains(r.round())) {
                missing.add(r.round());
            }
        }
        if (!missing.isEmpty()) {
            List<Integer> first = missing.stream().limit(20).toList();
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "회차 데이터에 없는 회차가 있어서 올릴 수 없어요. 회차 수집을 먼저 해 주세요. 없는 회차: " + first
                            + (missing.size() > 20 ? " 외 " + (missing.size() - 20) + "개" : ""),
                    "판매점 CSV 회차가 draw_round 에 없음: " + first);
        }
    }

    private static String checkKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String kind = raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NEW", "CHANGED", "DELETED").contains(kind)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구분은 NEW, CHANGED, DELETED 중 하나예요.",
                    "변경점 구분 값 오류: " + raw);
        }
        // 이 파일 종류는 삭제가 없다. 존재하지 않는 구분으로 걸러 빈 목록이 나오게 한다.
        return kind;
    }

    private byte[] readFile(long uploadId) {
        try {
            return files.read(uploadId);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "업로드한 파일이 서버에 없어요. 다시 업로드해 주세요.",
                    "판매점 업로드 파일 읽기 실패: id=" + uploadId + " / " + e);
        }
    }

    private StoreUploadRow findOrThrow(long uploadId) {
        return repo.find(uploadId).orElseThrow(() -> notFound(uploadId));
    }

    private static BusinessException notFound(long uploadId) {
        return new BusinessException(ErrorCode.NOT_FOUND, "업로드 기록을 찾을 수 없어요.", "판매점 업로드 없음: " + uploadId);
    }

    private static AdminPrincipal currentAdmin() {
        return AdminAccess.current().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요.", "관리자 세션 없음"));
    }
}
