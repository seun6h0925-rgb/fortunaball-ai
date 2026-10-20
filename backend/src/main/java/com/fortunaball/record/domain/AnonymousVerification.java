package com.fortunaball.record.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 익명 당첨 인증 한 건. (사용자가 올린 당첨 용지 사진 + 발급코드, 관리자 검수 대상)
 * DB 테이블: anonymous_verification
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - ticketHash 는 용지 QR 의 지문(SHA-256)이다. 용지 일련번호 원문은 어디에도 저장하지 않는다.
 *  - 테이블의 active_issue_id / active_ticket_hash 는 DB 가 계산하는 칸이라 이 엔티티에 없다. (코드에서 값을 넣지 않는다)
 *    두 칸 덕분에 "발급코드당 1건·같은 용지 1건"이 (반려 건을 뺀 채로) DB 에서 지켜진다.
 *    어기면 저장할 때 DataIntegrityViolationException 이 난다. (ProofRegisterService 가 안내 문구로 바꿔 준다)
 *  - 사진 파일은 file_upload 에 있고 여기서는 fileUploadId 로만 연결한다. (인증 1건당 사진 1개)
 *  - 연결은 객체 참조 없이 id 로만 한다. (이 프로젝트의 다른 엔티티와 같은 방식)
 *  - createdAt 은 DB 가 넣는다. reviewedAt 은 검수할 때 코드가 넣는다.
 */
@Entity
@Table(name = "anonymous_verification")
public class AnonymousVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "number_issue_id", nullable = false, updatable = false)
    private Long numberIssueId;

    @Column(name = "file_upload_id", nullable = false, updatable = false)
    private Long fileUploadId;

    @Column(name = "issue_code", nullable = false, updatable = false, length = 20)
    private String issueCode;

    @Column(name = "ticket_hash", nullable = false, updatable = false, length = 64)
    private String ticketHash;

    /** comment 는 MySQL 예약어와 비슷해서 역따옴표로 감싼다. */
    @Column(name = "`comment`", columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "is_public", nullable = false)
    private boolean publicVisible;

    @Column(name = "admin_comment", columnDefinition = "TEXT")
    private String adminComment;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected AnonymousVerification() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    /** 새로 등록한 인증. 항상 검수 대기·비공개로 시작한다. */
    public static AnonymousVerification register(Long numberIssueId, Long fileUploadId, String issueCode,
                                                 String ticketHash, String comment) {
        AnonymousVerification v = new AnonymousVerification();
        v.numberIssueId = numberIssueId;
        v.fileUploadId = fileUploadId;
        v.issueCode = issueCode;
        v.ticketHash = ticketHash;
        v.comment = comment;
        v.reviewStatus = ReviewStatus.PENDING;
        v.publicVisible = false;
        return v;
    }

    /**
     * 검수 결과를 기록한다. (관리자 검수 API 에서 쓴다)
     * 대기(PENDING)로 되돌리는 것은 허용하지 않는다.
     */
    public void review(ReviewStatus status, String adminComment, LocalDateTime now) {
        if (status == null || status == ReviewStatus.PENDING) {
            throw new IllegalArgumentException("검수 결과는 대기가 될 수 없습니다: " + status);
        }
        this.reviewStatus = status;
        this.adminComment = adminComment;
        this.reviewedAt = now;
        if (status != ReviewStatus.APPROVED) {
            this.publicVisible = false;   // 승인이 아니면 공개하지 않는다.
        }
    }

    public Long getId() { return id; }
    public Long getNumberIssueId() { return numberIssueId; }
    public Long getFileUploadId() { return fileUploadId; }
    public String getIssueCode() { return issueCode; }
    public String getTicketHash() { return ticketHash; }
    public String getComment() { return comment; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public boolean isPublicVisible() { return publicVisible; }
    public String getAdminComment() { return adminComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
