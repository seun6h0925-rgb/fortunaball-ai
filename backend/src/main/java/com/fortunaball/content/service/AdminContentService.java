package com.fortunaball.content.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.content.domain.ContentPage;
import com.fortunaball.content.domain.ContentType;
import com.fortunaball.content.dto.AdminContentResponse;
import com.fortunaball.content.dto.ContentUpdateRequest;
import com.fortunaball.content.repository.ContentPageRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안내 콘텐츠 관리 (API-58 목록, API-59 수정). 관리자 로그인이 필요하다. (SecurityConfig 가 막는다)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 글을 새로 만들거나 지우는 기능은 없다. 종류(ContentType)마다 한 줄이 시드 SQL 로 들어 있고 그 글만 고친다.
 *  - 법적 고지(NO_GUARANTEE, GAMBLING_WARNING 등)를 실수로 덮어써도 되돌릴 수 있게, 수정할 때
 *    "수정 전 내용 전체"를 admin_action_log 에 남긴다. (내용은 12000자까지만 허용해서 기록에 다 들어간다)
 */
@Service
public class AdminContentService {

    static final int TITLE_MAX = 200;
    static final int CONTENT_MAX = 12000;
    private static final String TABLE = "content_page";

    private final ContentPageRepository repository;
    private final AdminActionRecorder recorder;

    public AdminContentService(ContentPageRepository repository, AdminActionRecorder recorder) {
        this.repository = repository;
        this.recorder = recorder;
    }

    /** API-58: contentType 이 null 이면 전부, 있으면 그 종류만. 숨김 상태인 글도 보인다. */
    @Transactional(readOnly = true)
    public List<AdminContentResponse> list(String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            ContentType type = parse(contentType);
            return repository.findByPageType(type.name()).map(AdminContentResponse::from).stream().toList();
        }
        return repository.findAll().stream()
                // 화면에서 항상 같은 순서로 보이도록 ContentType 선언 순서로 정렬한다. (모르는 종류는 맨 뒤)
                .sorted(Comparator.comparingInt(p -> ContentType.from(p.getPageType()).map(Enum::ordinal).orElse(99)))
                .map(AdminContentResponse::from)
                .toList();
    }

    /** API-59: 제목·내용 수정 (보낸 항목만) */
    @Transactional
    public AdminContentResponse update(String contentType, ContentUpdateRequest request) {
        AdminPrincipal admin = currentAdmin();
        ContentType type = parse(contentType);
        if (request == null || (request.title() == null && request.content() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 제목이나 내용을 보내 주세요.", "콘텐츠 수정 요청이 비어 있음");
        }
        String title = request.title() == null ? null : checkTitle(request.title());
        String content = request.content() == null ? null : checkContent(request.content());

        ContentPage page = repository.findByPageType(type.name()).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, "안내 내용을 찾을 수 없어요.", "content_page 없음: " + type.name()));

        List<String> changes = new ArrayList<>();
        if (title != null && !title.equals(page.getTitle())) {
            changes.add("제목: '" + page.getTitle() + "' → '" + title + "'");
        }
        String before = page.getContent();
        if (content != null && !content.equals(before)) {
            changes.add("내용: " + before.length() + "자 → " + content.length() + "자");
        }

        String beforeContent = before;
        page.update(title, content);
        if (!changes.isEmpty()) {
            String detail = "콘텐츠 수정 - 종류: " + type.name() + ", " + String.join(", ", changes);
            if (content != null && !content.equals(beforeContent)) {
                detail += "\n[수정 전 내용 전체]\n" + beforeContent;
            }
            recorder.record(admin.id(), "UPDATE", TABLE, page.getId(), detail);
        }
        return AdminContentResponse.from(page);
    }

    // ------------------------------------------------------------------ 보조

    private static ContentType parse(String code) {
        return ContentType.from(code).orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_INPUT, "알 수 없는 안내 종류예요.", "contentType=" + code));
    }

    /** 관리자 번호는 로그인 세션에서만 가져온다. (요청에 담긴 값은 믿지 않는다) */
    private static AdminPrincipal currentAdmin() {
        Optional<AdminPrincipal> admin = AdminAccess.current();
        return admin.orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요.", "관리자 세션 없음"));
    }

    private static String checkTitle(String raw) {
        String title = raw.trim();
        if (title.isEmpty() || title.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "제목은 1자 이상 " + TITLE_MAX + "자 이하로 입력해 주세요.", "콘텐츠 제목 길이 오류: " + title.length());
        }
        return title;
    }

    private static String checkContent(String raw) {
        String content = raw.trim();
        if (content.isEmpty() || content.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "내용은 1자 이상 " + CONTENT_MAX + "자 이하로 입력해 주세요.", "콘텐츠 내용 길이 오류: " + content.length());
        }
        return content;
    }
}
