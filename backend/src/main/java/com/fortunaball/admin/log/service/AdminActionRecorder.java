package com.fortunaball.admin.log.service;

import com.fortunaball.admin.log.domain.AdminActionLog;
import com.fortunaball.admin.log.repository.AdminActionLogRepository;
import org.springframework.stereotype.Component;

/**
 * 관리자 작업 기록을 남기는 곳. 관리자 기능(로그인, 공지, 판매점 수정 등)은 모두 여기에 한 줄씩 남긴다.
 *
 * 쓰는 법: recorder.record(관리자 번호, "UPDATE", "notice", 공지번호, "제목: 'A' → 'B'");
 * ⚠ detail 에는 비밀번호·토큰·개인정보 원문을 넣지 않는다.
 */
@Component
public class AdminActionRecorder {

    private final AdminActionLogRepository repository;

    public AdminActionRecorder(AdminActionLogRepository repository) {
        this.repository = repository;
    }

    public void record(Long adminUserId, String actionType, String targetTable, Long targetId, String detail) {
        repository.save(new AdminActionLog(adminUserId, actionType, targetTable, targetId, detail));
    }
}
