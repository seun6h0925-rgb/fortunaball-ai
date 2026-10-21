package com.fortunaball.notice.service;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.notice.domain.Notice;
import com.fortunaball.notice.domain.NoticeType;
import com.fortunaball.notice.dto.AdminNoticeResponse;
import com.fortunaball.notice.dto.NoticeCreateRequest;
import com.fortunaball.notice.dto.NoticePageResponse;
import com.fortunaball.notice.dto.NoticeUpdateRequest;
import com.fortunaball.notice.repository.NoticeRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 관리 (API-35 등록, 36 수정, 37 삭제, 60 목록). 관리자 로그인이 필요하다. (SecurityConfig 가 막는다)
 *
 * ⚠ 등록·수정·삭제는 모두 admin_action_log 에 남긴다. 수정은 바뀐 항목의 "전 → 후" 값을 적는다.
 *    내용(content)은 길 수 있어서 기록에는 앞부분만 잘라 적는다.
 */
@Service
public class AdminNoticeService {

    static final int TITLE_MAX = 200;
    static final int CONTENT_MAX = 5000;
    /** 작업 기록에 적는 내용 최대 글자 수 */
    static final int LOG_SNIPPET = 100;
    private static final String TABLE = "notice";

    private final NoticeRepository repository;
    private final AdminActionRecorder recorder;

    public AdminNoticeService(NoticeRepository repository, AdminActionRecorder recorder) {
        this.repository = repository;
        this.recorder = recorder;
    }

    /** API-60: 관리자용 목록. visible 이 null 이면 전부, true/false 면 그 상태만 */
    @Transactional(readOnly = true)
    public NoticePageResponse<AdminNoticeResponse> list(Boolean visible, Integer page, Integer size) {
        PageRequest pr = PageRequest.of(NoticeService.safePage(page), NoticeService.safeSize(size));
        Page<Notice> result = visible == null
                ? repository.findAllByOrderByCreatedAtDescIdDesc(pr)
                : repository.findByVisibleOrderByCreatedAtDescIdDesc(visible, pr);
        return NoticePageResponse.from(result.map(AdminNoticeResponse::from));
    }

    /** API-35: 등록 */
    @Transactional
    public AdminNoticeResponse create(NoticeCreateRequest request) {
        AdminPrincipal admin = currentAdmin();
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목과 내용을 입력해 주세요.", "공지 등록 요청이 비어 있음");
        }
        String title = checkTitle(request.title());
        String content = checkContent(request.content());
        NoticeType type = request.noticeType() == null ? NoticeType.GENERAL : request.noticeType();
        boolean visible = request.visible() == null || request.visible();

        Notice saved = repository.save(new Notice(admin.id(), title, content, type, visible));
        recorder.record(admin.id(), "CREATE", TABLE, saved.getId(),
                "공지 등록 - 제목: '" + title + "', 종류: " + type + ", 공개: " + visible);
        return AdminNoticeResponse.from(saved);
    }

    /** API-36: 수정 (보낸 항목만) */
    @Transactional
    public AdminNoticeResponse update(Long noticeId, NoticeUpdateRequest request) {
        AdminPrincipal admin = currentAdmin();
        if (request == null || (request.title() == null && request.content() == null
                && request.noticeType() == null && request.visible() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 내용을 보내 주세요.", "공지 수정 요청이 비어 있음");
        }
        Notice notice = find(noticeId);

        String title = request.title() == null ? null : checkTitle(request.title());
        String content = request.content() == null ? null : checkContent(request.content());

        // 바뀐 항목만 "전 → 후"로 적는다. (값이 같으면 적지 않는다)
        List<String> changes = new ArrayList<>();
        if (title != null && !title.equals(notice.getTitle())) {
            changes.add("제목: '" + notice.getTitle() + "' → '" + title + "'");
        }
        if (content != null && !content.equals(notice.getContent())) {
            changes.add("내용: '" + snippet(notice.getContent()) + "' → '" + snippet(content) + "'");
        }
        if (request.noticeType() != null && request.noticeType() != notice.getNoticeType()) {
            changes.add("종류: " + notice.getNoticeType() + " → " + request.noticeType());
        }
        if (request.visible() != null && request.visible() != notice.isVisible()) {
            changes.add("공개: " + notice.isVisible() + " → " + request.visible());
        }

        notice.update(title, content, request.noticeType(), request.visible());
        if (!changes.isEmpty()) {
            recorder.record(admin.id(), "UPDATE", TABLE, noticeId, "공지 수정 - " + String.join(", ", changes));
        }
        return AdminNoticeResponse.from(notice);
    }

    /** API-37: 삭제 (진짜로 지운다. 지운 공지의 제목·종류·공개 여부는 작업 기록에 남는다) */
    @Transactional
    public void delete(Long noticeId) {
        AdminPrincipal admin = currentAdmin();
        Notice notice = find(noticeId);
        repository.delete(notice);
        recorder.record(admin.id(), "DELETE", TABLE, noticeId,
                "공지 삭제 - 제목: '" + notice.getTitle() + "', 종류: " + notice.getNoticeType()
                        + ", 공개: " + notice.isVisible());
    }

    // ------------------------------------------------------------------ 보조

    private Notice find(Long noticeId) {
        return repository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "공지사항을 찾을 수 없어요.", "공지 없음: " + noticeId));
    }

    /** 관리자 번호는 로그인 세션에서만 가져온다. (요청에 담긴 값은 믿지 않는다) */
    private static AdminPrincipal currentAdmin() {
        return AdminAccess.current().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요.", "관리자 세션 없음"));
    }

    private static String checkTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty() || title.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "제목은 1자 이상 " + TITLE_MAX + "자 이하로 입력해 주세요.", "공지 제목 길이 오류: " + title.length());
        }
        return title;
    }

    private static String checkContent(String raw) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty() || content.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "내용은 1자 이상 " + CONTENT_MAX + "자 이하로 입력해 주세요.", "공지 내용 길이 오류: " + content.length());
        }
        return content;
    }

    private static String snippet(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= LOG_SNIPPET ? oneLine : oneLine.substring(0, LOG_SNIPPET) + "…";
    }
}
