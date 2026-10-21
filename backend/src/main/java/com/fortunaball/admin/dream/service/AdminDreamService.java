package com.fortunaball.admin.dream.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.dream.dto.DreamNumberInput;
import com.fortunaball.admin.dream.dto.DreamNumberItem;
import com.fortunaball.admin.dream.dto.DreamSourceInput;
import com.fortunaball.admin.dream.dto.DreamSourceItem;
import com.fortunaball.admin.dream.dto.DreamSymbolCreateRequest;
import com.fortunaball.admin.dream.dto.DreamSymbolItem;
import com.fortunaball.admin.dream.dto.DreamSymbolUpdateRequest;
import com.fortunaball.admin.dream.repository.AdminDreamRepository;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 꿈 상징 관리 (API-57 목록, 33 등록, 34 수정·비활성화).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 꿈 풀이(DreamKnowledgeRepository)는 요청마다 DB 에서 상징·별칭·번호를 읽는다. 여기서 바꾼 내용은 바로 반영된다.
 *  - 상징 이름·분류는 등록 때만 정한다. 수정에서는 바꿀 수 없다. (같은 이름+분류는 중복 등록 불가)
 *  - 수정에서 aliases / numbers / sources 를 보내면 "그 목록이 전부"다. 빠진 것은 지워진다.
 *    비활성화(active=false)는 상징과 번호를 지우지 않고 꿈 풀이에서만 빼는 것이다.
 *  - 출처는 상징별로 연결한다. 이미 있는 출처는 sourceId 로 연결하고, 없으면 새 출처를 같이 만든다.
 *    (출처 테이블에는 사용 여부 칸이 없어서, 출처 단위 비활성화는 따로 없다. 그 출처가 달린 상징을 각각 비활성화한다)
 *  - 별칭은 상징 이름과 같을 수 없다. 1글자 상징·별칭은 매칭 규칙(조사 확인) 때문에 오인식이 잦으니 가급적 2글자 이상을 쓴다.
 *  - 등록·수정은 admin_action_log 에 남긴다. 수정은 바뀐 항목의 "전 → 후"를 적고, 목록 응답의 history 로 보여 준다.
 */
@Service
public class AdminDreamService {

    static final int KEYWORD_MAX = 100;
    static final int CATEGORY_MAX = 30;
    static final int DESCRIPTION_MAX = 2000;
    static final int ALIASES_MAX = 50;
    static final int NUMBERS_MAX = 10;
    static final int SOURCES_MAX = 10;
    static final int SOURCE_NAME_MAX = 100;
    static final int SOURCE_TYPE_MAX = 30;
    static final int SOURCE_URL_MAX = 500;
    static final int MEMO_MAX = 2000;
    static final String DEFAULT_CATEGORY = "기타";
    private static final Set<String> ORIGINS = Set.of("SERVICE", "SMORFIA", "CHARADA");
    private static final Set<String> TRUST = Set.of("HIGH", "MEDIUM", "LOW");
    private static final String TABLE = "dream_symbol";

    private final AdminDreamRepository repo;
    private final TransactionTemplate tx;
    private final AdminActionRecorder recorder;

    public AdminDreamService(AdminDreamRepository repo, TransactionTemplate tx, AdminActionRecorder recorder) {
        this.repo = repo;
        this.tx = tx;
        this.recorder = recorder;
    }

    /** 검사를 마친 출처 입력. id 가 있으면 기존 출처 연결, 없으면 새 출처. */
    record Src(Long id, String name, String type, String url, String trust, String memo) {
    }

    // ------------------------------------------------------------------ API-57 목록

    public AdminPage<DreamSymbolItem> list(String keyword, String source, Boolean active, Integer page, Integer size) {
        return repo.page(blankToNull(keyword), blankToNull(source), active, AdminQuery.page(page), AdminQuery.size(size));
    }

    // ------------------------------------------------------------------ API-33 등록

    public DreamSymbolItem create(DreamSymbolCreateRequest req) {
        AdminPrincipal admin = currentAdmin();
        if (req == null) {
            throw invalid("상징 내용을 보내 주세요.", "꿈 상징 등록 요청이 비어 있음");
        }
        String keyword = checkText(req.keyword(), KEYWORD_MAX, "상징 이름은 1자 이상 " + KEYWORD_MAX + "자 이하로 입력해 주세요.");
        String category = req.category() == null || req.category().isBlank() ? DEFAULT_CATEGORY
                : checkText(req.category(), CATEGORY_MAX, "분류는 1자 이상 " + CATEGORY_MAX + "자 이하로 입력해 주세요.");
        String description = checkDescription(req.description());
        List<String> aliases = checkAliases(req.aliases(), keyword);
        Map<Integer, String> numbers = checkNumbers(req.numbers());
        List<Src> sources = checkSources(req.sources());
        if (numbers.isEmpty()) {
            throw invalid("추천 번호를 1개 이상 넣어 주세요.", "꿈 상징 번호 없음");
        }
        if (sources.isEmpty()) {
            throw invalid("출처를 1개 이상 넣어 주세요.", "꿈 상징 출처 없음");
        }

        List<String> created = new ArrayList<>();
        long id;
        try {
            id = Objects.requireNonNull(tx.execute(status -> {
                long sid = repo.insertSymbol(keyword, category, description);
                for (String alias : aliases) {
                    repo.insertAlias(sid, alias);
                }
                for (Map.Entry<Integer, String> e : numbers.entrySet()) {
                    repo.insertNumber(sid, e.getKey(), e.getValue());
                }
                for (Map.Entry<Long, String> link : resolveSources(sources, created).entrySet()) {
                    repo.insertLink(sid, link.getKey(), link.getValue());
                }
                return sid;
            }));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "같은 이름과 분류의 상징이 이미 있어요.",
                    "꿈 상징 중복: " + keyword + "/" + category);
        }
        String sourceNote = created.isEmpty() ? "" : ", 새 출처 " + String.join(", ", created);
        recorder.record(admin.id(), "CREATE", TABLE, id, keyword + "(" + category + ") 등록: 별칭 " + aliases.size()
                + "개, 번호 " + numbers.keySet() + ", 출처 " + sources.size() + "개" + sourceNote);
        return repo.find(id).orElseThrow(() -> new IllegalStateException("등록한 상징을 읽지 못했습니다: " + id));
    }

    // ------------------------------------------------------------------ API-34 수정·비활성화

    public DreamSymbolItem update(long symbolId, DreamSymbolUpdateRequest req) {
        AdminPrincipal admin = currentAdmin();
        if (req == null || (req.description() == null && req.aliases() == null && req.numbers() == null
                && req.sources() == null && req.active() == null)) {
            throw invalid("수정할 내용을 보내 주세요.", "꿈 상징 수정 요청이 비어 있음");
        }
        String newDescription = req.description() == null ? null : checkDescription(req.description());
        Map<Integer, String> newNumbers = null;
        if (req.numbers() != null) {
            newNumbers = checkNumbers(req.numbers());
            if (newNumbers.isEmpty()) {
                throw invalid("추천 번호를 1개 이상 넣어 주세요.", "꿈 상징 번호 없음");
            }
        }
        List<Src> newSources = null;
        if (req.sources() != null) {
            newSources = checkSources(req.sources());
            if (newSources.isEmpty()) {
                throw invalid("출처를 1개 이상 넣어 주세요.", "꿈 상징 출처 없음");
            }
        }
        final Map<Integer, String> wantedNumbers = newNumbers;
        final List<Src> wantedSources = newSources;

        List<String> changes = tx.execute(status -> {
            DreamSymbolItem old = repo.find(symbolId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                    "꿈 상징을 찾을 수 없어요.", "꿈 상징 없음: " + symbolId));
            List<String> diffs = new ArrayList<>();
            String description = newDescription == null ? old.description() : newDescription;
            boolean active = req.active() == null ? old.active() : req.active();
            int before = diffs.size();
            diff(diffs, "설명", snippet(old.description()), snippet(description));
            diff(diffs, "활성", String.valueOf(old.active()), String.valueOf(active));
            if (diffs.size() > before) {
                repo.updateSymbol(symbolId, description, active);
            }
            if (req.aliases() != null) {
                syncAliases(symbolId, old, checkAliases(req.aliases(), old.keyword()), diffs);
            }
            if (wantedNumbers != null) {
                syncNumbers(symbolId, old, wantedNumbers, diffs);
            }
            if (wantedSources != null) {
                syncSources(symbolId, old, wantedSources, diffs);
            }
            return diffs;
        });
        if (changes != null && !changes.isEmpty()) {
            recorder.record(admin.id(), "UPDATE", TABLE, symbolId, String.join(" / ", changes));
        }
        return repo.find(symbolId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                "꿈 상징을 찾을 수 없어요.", "꿈 상징 없음: " + symbolId));
    }

    private void syncAliases(long symbolId, DreamSymbolItem old, List<String> wanted, List<String> diffs) {
        for (String alias : old.aliases()) {
            if (!wanted.contains(alias)) {
                repo.deleteAlias(symbolId, alias);
                diffs.add("별칭 삭제: " + alias);
            }
        }
        for (String alias : wanted) {
            if (!old.aliases().contains(alias)) {
                repo.insertAlias(symbolId, alias);
                diffs.add("별칭 추가: " + alias);
            }
        }
    }

    private void syncNumbers(long symbolId, DreamSymbolItem old, Map<Integer, String> wanted, List<String> diffs) {
        Map<Integer, String> oldMap = new LinkedHashMap<>();
        for (DreamNumberItem n : old.numbers()) {
            oldMap.put(n.number(), n.origin());
        }
        for (Map.Entry<Integer, String> e : oldMap.entrySet()) {
            String origin = wanted.get(e.getKey());
            if (origin == null) {
                repo.deleteNumber(symbolId, e.getKey());
                diffs.add("번호 삭제: " + e.getKey());
            } else if (!origin.equals(e.getValue())) {
                repo.updateNumberOrigin(symbolId, e.getKey(), origin);
                diffs.add("번호 출처 변경: " + e.getKey() + " (" + e.getValue() + " → " + origin + ")");
            }
        }
        for (Map.Entry<Integer, String> e : wanted.entrySet()) {
            if (!oldMap.containsKey(e.getKey())) {
                repo.insertNumber(symbolId, e.getKey(), e.getValue());
                diffs.add("번호 추가: " + e.getKey());
            }
        }
    }

    private void syncSources(long symbolId, DreamSymbolItem old, List<Src> wanted, List<String> diffs) {
        List<String> created = new ArrayList<>();
        Map<Long, String> links = resolveSources(wanted, created);
        for (String c : created) {
            diffs.add("새 출처 등록: " + c);
        }
        Map<Long, DreamSourceItem> oldMap = new LinkedHashMap<>();
        for (DreamSourceItem s : old.sources()) {
            oldMap.put(s.id(), s);
        }
        for (DreamSourceItem s : old.sources()) {
            if (!links.containsKey(s.id())) {
                repo.deleteLink(symbolId, s.id());
                diffs.add("출처 연결 해제: " + s.sourceName());
            }
        }
        for (Map.Entry<Long, String> e : links.entrySet()) {
            DreamSourceItem existing = oldMap.get(e.getKey());
            if (existing == null) {
                repo.insertLink(symbolId, e.getKey(), e.getValue());
                diffs.add("출처 연결: #" + e.getKey());
            } else if (!Objects.equals(existing.memo(), e.getValue())) {
                repo.updateLinkMemo(symbolId, e.getKey(), e.getValue());
                diffs.add("출처 메모 수정: " + existing.sourceName());
            }
        }
    }

    /** 출처 입력을 출처 번호 → 연결 메모로 바꾼다. 새 출처는 여기서 만든다. 거래(트랜잭션) 안에서 불러야 한다. */
    private Map<Long, String> resolveSources(List<Src> sources, List<String> createdNames) {
        Map<Long, String> links = new LinkedHashMap<>();
        for (Src s : sources) {
            long sid;
            if (s.id() != null) {
                if (!repo.sourceExists(s.id())) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "출처 #" + s.id() + " 를 찾을 수 없어요.",
                            "꿈 출처 없음: " + s.id());
                }
                sid = s.id();
            } else {
                sid = repo.insertSource(s.name(), s.type(), s.url(), s.trust(), null);
                createdNames.add(s.name() + "(#" + sid + ")");
            }
            links.putIfAbsent(sid, s.memo());
        }
        return links;
    }

    // ------------------------------------------------------------------ 입력 검사

    static String checkText(String raw, int max, String message) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.length() > max) {
            throw invalid(message, "꿈 상징 글자 길이 오류: " + v.length());
        }
        return v;
    }

    static String checkDescription(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.length() > DESCRIPTION_MAX) {
            throw invalid("설명은 1자 이상 " + DESCRIPTION_MAX + "자 이하로 입력해 주세요.", "꿈 상징 설명 길이 오류: " + v.length());
        }
        return v;
    }

    /** 별칭을 다듬어 모은다(앞뒤 공백 제거, 중복 제거). 상징 이름과 같은 별칭은 막는다. */
    static List<String> checkAliases(List<String> raw, String keyword) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) {
            return List.of();
        }
        if (raw.size() > ALIASES_MAX) {
            throw invalid("별칭은 " + ALIASES_MAX + "개까지 넣을 수 있어요.", "꿈 상징 별칭 개수 초과: " + raw.size());
        }
        for (String a : raw) {
            String v = a == null ? "" : a.trim();
            if (v.isEmpty() || v.length() > KEYWORD_MAX) {
                throw invalid("별칭은 1자 이상 " + KEYWORD_MAX + "자 이하여야 해요.", "꿈 상징 별칭 길이 오류");
            }
            if (v.equals(keyword)) {
                throw invalid("별칭이 상징 이름과 같아요: " + v, "꿈 상징 별칭=이름: " + v);
            }
            result.add(v);
        }
        return new ArrayList<>(result);
    }

    /** 번호 → 출처(origin). 1~45, 중복은 처음 것만. */
    static Map<Integer, String> checkNumbers(List<DreamNumberInput> raw) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        if (raw.size() > NUMBERS_MAX) {
            throw invalid("추천 번호는 " + NUMBERS_MAX + "개까지 넣을 수 있어요.", "꿈 상징 번호 개수 초과: " + raw.size());
        }
        for (DreamNumberInput in : raw) {
            Integer n = in == null ? null : in.number();
            if (n == null || n < 1 || n > 45) {
                throw invalid("추천 번호는 1~45 사이 숫자여야 해요.", "꿈 상징 번호 범위 오류: " + n);
            }
            String origin = in.origin() == null || in.origin().isBlank() ? "SERVICE"
                    : in.origin().trim().toUpperCase(Locale.ROOT);
            if (!ORIGINS.contains(origin)) {
                throw invalid("번호 출처는 SERVICE, SMORFIA, CHARADA 중 하나예요.", "꿈 상징 번호 출처 오류: " + in.origin());
            }
            result.putIfAbsent(n, origin);
        }
        return result;
    }

    static List<Src> checkSources(List<DreamSourceInput> raw) {
        List<Src> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw.size() > SOURCES_MAX) {
            throw invalid("출처는 " + SOURCES_MAX + "개까지 넣을 수 있어요.", "꿈 상징 출처 개수 초과: " + raw.size());
        }
        for (DreamSourceInput in : raw) {
            if (in == null) {
                throw invalid("출처 내용이 비어 있어요.", "꿈 상징 출처 null");
            }
            String memo = blankToNull(in.memo());
            if (memo != null && memo.length() > MEMO_MAX) {
                throw invalid("출처 메모는 " + MEMO_MAX + "자 이하로 입력해 주세요.", "꿈 상징 출처 메모 길이 오류");
            }
            if (in.sourceId() != null) {
                result.add(new Src(in.sourceId(), null, null, null, null, memo == null ? null : memo.trim()));
                continue;
            }
            String name = checkText(in.sourceName(), SOURCE_NAME_MAX, "새 출처의 이름은 1자 이상 " + SOURCE_NAME_MAX + "자 이하로 입력해 주세요.");
            String type = checkText(in.sourceType(), SOURCE_TYPE_MAX, "새 출처의 종류는 1자 이상 " + SOURCE_TYPE_MAX + "자 이하로 입력해 주세요. (예: 민속자료, 학술자료, 책, 웹, 심리학)");
            String url = blankToNull(in.sourceUrl());
            if (url != null && url.trim().length() > SOURCE_URL_MAX) {
                throw invalid("출처 주소는 " + SOURCE_URL_MAX + "자 이하로 입력해 주세요.", "꿈 출처 URL 길이 오류");
            }
            String trust = in.trustLevel() == null || in.trustLevel().isBlank() ? "LOW"
                    : in.trustLevel().trim().toUpperCase(Locale.ROOT);
            if (!TRUST.contains(trust)) {
                throw invalid("신뢰도는 HIGH, MEDIUM, LOW 중 하나예요.", "꿈 출처 신뢰도 오류: " + in.trustLevel());
            }
            result.add(new Src(null, name, type, url == null ? null : url.trim(), trust, memo == null ? null : memo.trim()));
        }
        return result;
    }

    // ------------------------------------------------------------------ 보조

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static void diff(List<String> diffs, String label, String before, String after) {
        if (!Objects.equals(before, after)) {
            diffs.add(label + ": '" + before + "' → '" + after + "'");
        }
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 60) + "…";
    }

    private static BusinessException invalid(String user, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, user, detail);
    }

    private static AdminPrincipal currentAdmin() {
        return AdminAccess.current().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요.", "관리자 세션 없음"));
    }
}
