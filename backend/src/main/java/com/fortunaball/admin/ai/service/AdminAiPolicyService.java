package com.fortunaball.admin.ai.service;

import com.fortunaball.admin.ai.dto.AiPolicyCreateRequest;
import com.fortunaball.admin.ai.dto.AiPolicyItem;
import com.fortunaball.admin.ai.dto.AiPolicyKeywordInput;
import com.fortunaball.admin.ai.dto.AiPolicyKeywordItem;
import com.fortunaball.admin.ai.dto.AiPolicyUpdateRequest;
import com.fortunaball.admin.ai.repository.AdminAiPolicyRepository;
import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 AI 입력 정책 관리 (API-54 목록, 55 등록, 56 수정·비활성화). 정책은 ai_input_policy, 키워드는 ai_policy_keyword.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 서비스(AiPolicyEngine)는 정책을 1분 동안 기억해서 쓴다(CachedAiPolicySource). 그래서 여기서 바꾼 내용은 "최대 1분 안에" 반영된다.
 *  - 지금 AI 질문 검사 엔진은 처리 방식이 FIXED_RESPONSE 인 정책만 읽고, 키워드가 하나도 없는 정책은 걸리지 않는다.
 *    AI_REVIEW 정책은 등록할 수 있지만 엔진이 아직 쓰지 않는다. 일치 방식(EXACT/CONTAINS)도 엔진은 아직 구분하지 않고 "포함"으로 본다.
 *  - 키워드는 공백을 모두 지우고 소문자로 바꿔 저장한다. (엔진도 질문의 공백을 지우고 비교한다. 시드 파일의 저장 방식과 같다)
 *  - 정책 코드·기능 범위·유형·처리 방식은 등록 때만 정한다. 수정에서는 바꿀 수 없다.
 *    (코드마다 엔진이 다르게 판단하는 정책이 있어서, 코드를 바꾸면 의미가 달라진다)
 *  - 수정에서 keywords 를 보내면 "그 목록이 전부"다. 목록에 없는 기존 키워드는 삭제된다. 키워드만 잠깐 끄려면 active=false 로 보낸다.
 *  - 비활성화(active=false)해도 정책과 키워드는 지워지지 않는다. 안전 안내 정책을 끄면 그 질문은 AI 로 넘어가므로 주의가 필요하다.
 *  - 등록·수정은 admin_action_log 에 남긴다. 수정은 바뀐 항목의 "전 → 후"를 적는다.
 */
@Service
public class AdminAiPolicyService {

    static final int NAME_MAX = 100;
    static final int MESSAGE_MAX = 2000;
    static final int KEYWORD_MAX = 255;
    static final int KEYWORDS_MAX = 100;
    static final int PRIORITY_MIN = 1;
    static final int PRIORITY_MAX = 9999;
    static final int LOG_SNIPPET = 60;
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    private static final Pattern TYPE = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final Set<String> SCOPES = Set.of("ANALYST", "DREAM", "ALL");
    private static final Set<String> ACTIONS = Set.of("FIXED_RESPONSE", "AI_REVIEW");
    private static final Set<String> MATCH_TYPES = Set.of("EXACT", "CONTAINS");
    private static final String TABLE = "ai_input_policy";

    private final AdminAiPolicyRepository repo;
    private final TransactionTemplate tx;
    private final AdminActionRecorder recorder;

    public AdminAiPolicyService(AdminAiPolicyRepository repo, TransactionTemplate tx, AdminActionRecorder recorder) {
        this.repo = repo;
        this.tx = tx;
        this.recorder = recorder;
    }

    // ------------------------------------------------------------------ API-54 목록

    public List<AiPolicyItem> list(String featureScope, Boolean active) {
        String scope = null;
        if (featureScope != null && !featureScope.isBlank()) {
            scope = featureScope.trim().toUpperCase(Locale.ROOT);
            if (!SCOPES.contains(scope)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "기능 범위는 ANALYST, DREAM, ALL 중 하나예요.",
                        "AI 정책 기능 범위 값 오류: " + featureScope);
            }
        }
        return repo.list(scope, active);
    }

    // ------------------------------------------------------------------ API-55 등록

    public AiPolicyItem create(AiPolicyCreateRequest req) {
        AdminPrincipal admin = currentAdmin();
        if (req == null) {
            throw invalid("정책 내용을 보내 주세요.", "AI 정책 등록 요청이 비어 있음");
        }
        String code = req.policyCode() == null ? "" : req.policyCode().trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw invalid("정책 코드는 영문 대문자·숫자·밑줄(_)로 2~50자, 영문으로 시작해야 해요.", "AI 정책 코드 형식 오류: " + req.policyCode());
        }
        String name = checkName(req.policyName());
        String scope = checkEnum(req.featureScope(), SCOPES, "기능 범위는 ANALYST, DREAM, ALL 중 하나예요.");
        String type = req.policyType() == null ? "" : req.policyType().trim().toUpperCase(Locale.ROOT);
        if (!TYPE.matcher(type).matches()) {
            throw invalid("정책 유형은 영문 대문자·숫자·밑줄(_)로 2~30자, 영문으로 시작해야 해요.", "AI 정책 유형 형식 오류: " + req.policyType());
        }
        String action = checkEnum(req.actionType(), ACTIONS, "처리 방식은 FIXED_RESPONSE, AI_REVIEW 중 하나예요.");
        int priority = checkPriority(req.priority());
        String message = checkMessage(req.responseMessage());
        Map<String, AiPolicyKeywordInput> keywords = checkKeywords(req.keywords());

        long id;
        try {
            id = Objects.requireNonNull(tx.execute(status -> {
                long pid = repo.insertPolicy(code, name, scope, type, action, priority, message);
                for (Map.Entry<String, AiPolicyKeywordInput> e : keywords.entrySet()) {
                    repo.insertKeyword(pid, e.getKey(), matchType(e.getValue()), activeOf(e.getValue()));
                }
                return pid;
            }));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "같은 정책 코드가 이미 있어요.", "AI 정책 코드 중복: " + code);
        }
        recorder.record(admin.id(), "CREATE", TABLE, id, code + " 등록: 범위 " + scope + ", 유형 " + type + ", 처리 " + action
                + ", 우선순위 " + priority + ", 키워드 " + keywords.size() + "개");
        return repo.find(id).orElseThrow(() -> new IllegalStateException("등록한 정책을 읽지 못했습니다: " + id));
    }

    // ------------------------------------------------------------------ API-56 수정·비활성화

    public AiPolicyItem update(long policyId, AiPolicyUpdateRequest req) {
        AdminPrincipal admin = currentAdmin();
        if (req == null || (req.policyName() == null && req.priority() == null && req.responseMessage() == null
                && req.keywords() == null && req.active() == null)) {
            throw invalid("수정할 내용을 보내 주세요.", "AI 정책 수정 요청이 비어 있음");
        }
        String newName = req.policyName() == null ? null : checkName(req.policyName());
        Integer newPriority = req.priority() == null ? null : checkPriority(req.priority());
        String newMessage = req.responseMessage() == null ? null : checkMessage(req.responseMessage());
        Map<String, AiPolicyKeywordInput> newKeywords = req.keywords() == null ? null : checkKeywords(req.keywords());

        List<String> changes = tx.execute(status -> {
            AiPolicyItem old = repo.find(policyId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                    "정책을 찾을 수 없어요.", "AI 정책 없음: " + policyId));
            List<String> diffs = new ArrayList<>();
            String name = newName == null ? old.policyName() : newName;
            int priority = newPriority == null ? old.priority() : newPriority;
            String message = newMessage == null ? old.responseMessage() : newMessage;
            boolean active = req.active() == null ? old.active() : req.active();
            diff(diffs, "이름", old.policyName(), name);
            diff(diffs, "우선순위", String.valueOf(old.priority()), String.valueOf(priority));
            diff(diffs, "고정응답", snippet(old.responseMessage()), snippet(message));
            diff(diffs, "활성", String.valueOf(old.active()), String.valueOf(active));
            if (!diffs.isEmpty()) {
                repo.updatePolicy(policyId, name, priority, message, active);
            }
            if (newKeywords != null) {
                syncKeywords(policyId, old.keywords(), newKeywords, diffs);
            }
            return diffs;
        });
        if (changes != null && !changes.isEmpty()) {
            recorder.record(admin.id(), "UPDATE", TABLE, policyId, String.join(" / ", changes));
        }
        return repo.find(policyId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                "정책을 찾을 수 없어요.", "AI 정책 없음: " + policyId));
    }

    /** 기존 키워드와 새 목록을 견줘 삭제·추가·수정한다. 같은 키워드는 번호를 그대로 둔다. */
    private void syncKeywords(long policyId, List<AiPolicyKeywordItem> old, Map<String, AiPolicyKeywordInput> wanted,
                              List<String> diffs) {
        Map<String, AiPolicyKeywordItem> oldByText = new LinkedHashMap<>();
        for (AiPolicyKeywordItem k : old) {
            oldByText.put(k.keyword(), k);
        }
        for (AiPolicyKeywordItem k : old) {
            AiPolicyKeywordInput in = wanted.get(k.keyword());
            if (in == null) {
                repo.deleteKeyword(k.id());
                diffs.add("키워드 삭제: " + k.keyword());
            } else if (!matchType(in).equals(k.matchType()) || activeOf(in) != k.active()) {
                repo.updateKeyword(k.id(), matchType(in), activeOf(in));
                diffs.add("키워드 수정: " + k.keyword() + " (" + k.matchType() + "/" + k.active() + " → "
                        + matchType(in) + "/" + activeOf(in) + ")");
            }
        }
        for (Map.Entry<String, AiPolicyKeywordInput> e : wanted.entrySet()) {
            if (!oldByText.containsKey(e.getKey())) {
                repo.insertKeyword(policyId, e.getKey(), matchType(e.getValue()), activeOf(e.getValue()));
                diffs.add("키워드 추가: " + e.getKey());
            }
        }
    }

    // ------------------------------------------------------------------ 입력 검사

    private static String checkName(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.length() > NAME_MAX) {
            throw invalid("정책 이름은 1자 이상 " + NAME_MAX + "자 이하로 입력해 주세요.", "AI 정책 이름 길이 오류: " + v.length());
        }
        return v;
    }

    private static String checkMessage(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.length() > MESSAGE_MAX) {
            throw invalid("안내 문구는 1자 이상 " + MESSAGE_MAX + "자 이하로 입력해 주세요.", "AI 정책 문구 길이 오류: " + v.length());
        }
        return v;
    }

    private static int checkPriority(Integer p) {
        if (p == null || p < PRIORITY_MIN || p > PRIORITY_MAX) {
            throw invalid("우선순위는 " + PRIORITY_MIN + " 이상 " + PRIORITY_MAX + " 이하 숫자로 입력해 주세요. (작을수록 먼저 검사)",
                    "AI 정책 우선순위 오류: " + p);
        }
        return p;
    }

    private static String checkEnum(String raw, Set<String> allowed, String message) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw invalid(message, "AI 정책 값 오류: " + raw);
        }
        return v;
    }

    /** 키워드를 공백 제거·소문자로 바꿔 모은다. 같은 키워드가 또 나오면 처음 것만 쓴다. */
    private static Map<String, AiPolicyKeywordInput> checkKeywords(List<AiPolicyKeywordInput> raw) {
        Map<String, AiPolicyKeywordInput> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        if (raw.size() > KEYWORDS_MAX) {
            throw invalid("키워드는 한 정책에 " + KEYWORDS_MAX + "개까지 넣을 수 있어요.", "AI 정책 키워드 개수 초과: " + raw.size());
        }
        for (AiPolicyKeywordInput in : raw) {
            String k = normalizeKeyword(in == null ? null : in.keyword());
            if (k.isEmpty() || k.length() > KEYWORD_MAX) {
                throw invalid("키워드는 공백을 뺀 1자 이상 " + KEYWORD_MAX + "자 이하여야 해요.", "AI 정책 키워드 길이 오류");
            }
            String mt = in.matchType() == null || in.matchType().isBlank() ? "CONTAINS"
                    : in.matchType().trim().toUpperCase(Locale.ROOT);
            if (!MATCH_TYPES.contains(mt)) {
                throw invalid("일치 방식은 EXACT, CONTAINS 중 하나예요.", "AI 정책 일치 방식 오류: " + in.matchType());
            }
            result.putIfAbsent(k, new AiPolicyKeywordInput(k, mt, in.active() == null ? Boolean.TRUE : in.active()));
        }
        return result;
    }

    static String normalizeKeyword(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String matchType(AiPolicyKeywordInput in) {
        return in.matchType();
    }

    private static boolean activeOf(AiPolicyKeywordInput in) {
        return Boolean.TRUE.equals(in.active());
    }

    // ------------------------------------------------------------------ 보조

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
        return oneLine.length() <= LOG_SNIPPET ? oneLine : oneLine.substring(0, LOG_SNIPPET) + "…";
    }

    private static BusinessException invalid(String user, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, user, detail);
    }

    private static AdminPrincipal currentAdmin() {
        return AdminAccess.current().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요.", "관리자 세션 없음"));
    }
}
