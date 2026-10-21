package com.fortunaball.admin.store.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.common.AdminQuery;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.admin.store.dto.AdminStoreItem;
import com.fortunaball.admin.store.dto.StoreUpdateRequest;
import com.fortunaball.admin.store.repository.AdminStoreRepository;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 판매점 목록(API-53)과 수정(API-31).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 수정은 "좌표 변환 실패 보정" 같은 예외 상황용이다. 건별 등록은 없다. (판매점은 파일 업로드로만 늘어난다)
 *  - 보내지 않은(null) 항목은 바꾸지 않는다. 주소·지역에 빈 글자를 보내면 값을 비운다. 판매점명은 비울 수 없다.
 *  - 좌표를 바꾸면 위도·경도를 함께 보내야 하고, 좌표 변환 상태는 SUCCESS 로 바뀐다. (사람이 직접 확인한 좌표로 본다)
 *  - 좌표 범위는 대한민국 일대(위도 33~39, 경도 124~132)만 받는다. 값이 뒤바뀌어 들어오는 실수를 막는다.
 *  - 판매점명 + 도로명 주소가 다른 판매점과 같아지면 DB 규칙(UNIQUE)에 걸린다. 이때는 409 로 알려 준다.
 *  - 바뀐 항목의 "전 → 후"를 admin_action_log 에 남긴다.
 */
@Service
public class AdminStoreService {

    static final int NAME_MAX = 100;
    static final int ADDRESS_MAX = 255;
    static final int SIDO_MAX = 30;
    static final int SIGUNGU_MAX = 50;
    static final BigDecimal LAT_MIN = new BigDecimal("33");
    static final BigDecimal LAT_MAX = new BigDecimal("39");
    static final BigDecimal LON_MIN = new BigDecimal("124");
    static final BigDecimal LON_MAX = new BigDecimal("132");
    private static final Set<String> GEO_STATUSES = Set.of("PENDING", "SUCCESS", "FAILED");
    private static final String TABLE = "lotto_store";

    private final AdminStoreRepository repo;
    private final AdminActionRecorder recorder;

    public AdminStoreService(AdminStoreRepository repo, AdminActionRecorder recorder) {
        this.repo = repo;
        this.recorder = recorder;
    }

    // ------------------------------------------------------------------ API-53 목록

    @Transactional(readOnly = true)
    public AdminPage<AdminStoreItem> list(String sido, String sigungu, String keyword, String geocodeStatus,
                                          Boolean closed, Integer page, Integer size) {
        String geo = null;
        if (geocodeStatus != null && !geocodeStatus.isBlank()) {
            geo = geocodeStatus.trim().toUpperCase(Locale.ROOT);
            if (!GEO_STATUSES.contains(geo)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "좌표 변환 상태는 PENDING, SUCCESS, FAILED 중 하나예요.",
                        "좌표 변환 상태 값 오류: " + geocodeStatus);
            }
        }
        return repo.list(blankToNull(sido), blankToNull(sigungu), blankToNull(keyword), geo, closed,
                AdminQuery.page(page), AdminQuery.size(size));
    }

    // ------------------------------------------------------------------ API-31 수정

    @Transactional
    public AdminStoreItem update(long storeId, StoreUpdateRequest req) {
        AdminPrincipal admin = currentAdmin();
        if (req == null || (req.storeName() == null && req.roadAddress() == null && req.jibunAddress() == null
                && req.sido() == null && req.sigungu() == null && req.latitude() == null && req.longitude() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 내용을 보내 주세요.", "판매점 수정 요청이 비어 있음");
        }
        AdminStoreItem old = repo.find(storeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                "판매점을 찾을 수 없어요.", "판매점 없음: " + storeId));

        String name = req.storeName() == null ? old.storeName() : checkName(req.storeName());
        String road = req.roadAddress() == null ? old.roadAddress() : checkText(req.roadAddress(), ADDRESS_MAX, "도로명 주소");
        String jibun = req.jibunAddress() == null ? old.jibunAddress() : checkText(req.jibunAddress(), ADDRESS_MAX, "지번 주소");
        String sido = req.sido() == null ? old.sido() : checkText(req.sido(), SIDO_MAX, "시도");
        String sigungu = req.sigungu() == null ? old.sigungu() : checkText(req.sigungu(), SIGUNGU_MAX, "시군구");

        BigDecimal lat = old.latitude();
        BigDecimal lon = old.longitude();
        boolean coordsGiven = req.latitude() != null || req.longitude() != null;
        if (coordsGiven) {
            if (req.latitude() == null || req.longitude() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "위도와 경도를 함께 보내 주세요.", "좌표 한쪽만 전달됨");
            }
            checkRange(req.latitude(), LAT_MIN, LAT_MAX, "위도");
            checkRange(req.longitude(), LON_MIN, LON_MAX, "경도");
            lat = req.latitude();
            lon = req.longitude();
        }
        boolean coordsChanged = coordsGiven && !(same(old.latitude(), lat) && same(old.longitude(), lon));
        String geoStatus = coordsChanged ? "SUCCESS" : old.geocodeStatus();

        List<String> changes = new ArrayList<>();
        diff(changes, "판매점명", old.storeName(), name);
        diff(changes, "도로명주소", old.roadAddress(), road);
        diff(changes, "지번주소", old.jibunAddress(), jibun);
        diff(changes, "시도", old.sido(), sido);
        diff(changes, "시군구", old.sigungu(), sigungu);
        if (coordsChanged) {
            diff(changes, "위도", str(old.latitude()), str(lat));
            diff(changes, "경도", str(old.longitude()), str(lon));
            diff(changes, "좌표변환상태", old.geocodeStatus(), geoStatus);
        }
        if (changes.isEmpty()) {
            return old;   // 바뀐 것이 없으면 DB 도 기록도 건드리지 않는다
        }
        try {
            repo.update(storeId, name, road, jibun, sido, sigungu, lat, lon, geoStatus, coordsChanged,
                    LocalDateTime.now(AdminQuery.ZONE));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "같은 이름·도로명 주소의 판매점이 이미 있어요.",
                    "판매점 수정 중복: id=" + storeId + " / " + e.getMostSpecificCause().getMessage());
        }
        recorder.record(admin.id(), "UPDATE", TABLE, storeId, String.join(" / ", changes));
        return repo.find(storeId).orElse(old);
    }

    // ------------------------------------------------------------------ 보조

    private static String checkName(String raw) {
        String v = raw.trim();
        if (v.isEmpty() || v.length() > NAME_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "판매점명은 1자 이상 " + NAME_MAX + "자 이하로 입력해 주세요.", "판매점명 길이 오류: " + v.length());
        }
        return v;
    }

    /** 빈 글자는 값을 비우는 것(null)으로 본다. */
    private static String checkText(String raw, int max, String label) {
        String v = raw.trim();
        if (v.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + "는 " + max + "자 이하로 입력해 주세요.",
                    label + " 길이 오류: " + v.length());
        }
        return v.isEmpty() ? null : v;
    }

    private static void checkRange(BigDecimal v, BigDecimal min, BigDecimal max, String label) {
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    label + " 값이 대한민국 범위(" + min.toPlainString() + "~" + max.toPlainString() + ")를 벗어났어요.",
                    label + " 범위 오류: " + v);
        }
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == null && b == null : a.compareTo(b) == 0;
    }

    private static void diff(List<String> changes, String label, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(label + ": '" + (before == null ? "" : before) + "' → '" + (after == null ? "" : after) + "'");
        }
    }

    private static String str(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static AdminPrincipal currentAdmin() {
        return AdminAccess.current().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요.", "관리자 세션 없음"));
    }
}
