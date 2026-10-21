package com.fortunaball.admin.log.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 관리자 작업 기록 (admin_action_log). 로그인·로그아웃, 데이터를 바꾼 작업을 남긴다.
 * 수정 작업은 actionDetail 에 변경 전·후 값을 함께 적는다. 비밀번호·개인정보 원문은 적지 않는다.
 */
@Entity
@Table(name = "admin_action_log")
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "target_table", nullable = false, length = 50)
    private String targetTable;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "action_detail", nullable = false, columnDefinition = "TEXT")
    private String actionDetail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminActionLog() {
    }

    public AdminActionLog(Long adminUserId, String actionType, String targetTable, Long targetId, String actionDetail) {
        this.adminUserId = adminUserId;
        this.actionType = actionType;
        this.targetTable = targetTable;
        this.targetId = targetId;
        this.actionDetail = actionDetail;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAdminUserId() { return adminUserId; }
    public String getActionType() { return actionType; }
    public String getTargetTable() { return targetTable; }
    public Long getTargetId() { return targetId; }
    public String getActionDetail() { return actionDetail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
