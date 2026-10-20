package com.fortunaball.record.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import com.fortunaball.record.dto.AdminVerificationItem;
import com.fortunaball.record.dto.AdminVerificationListResponse;
import com.fortunaball.record.dto.VerificationReviewRequest;
import com.fortunaball.record.dto.VerificationReviewResponse;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 인증 검수 (API-28 목록, API-29 검수 처리).
 * 📚 배운 내용: @Service, @Transactional(readOnly)
 *
 * 상태 이동 규칙 (이 밖의 이동은 409)
 *   PENDING  → APPROVED(승인) / REJECTED(반려)
 *   APPROVED → HIDDEN(숨김)   / REJECTED(반려)
 *   HIDDEN   → APPROVED(다시 공개) / REJECTED(반려)
 *   REJECTED → (끝) 사용자가 같은 발급코드로 다시 등록하면 새 인증 건이 생긴다.
 * 반려·숨김은 사유(관리자 코멘트)가 필수다. 승인되면 공개(is_public=1), 그 외는 비공개로 바뀐다.
 *
 * 공개 연동: 승인되면 그 발급 건의 당첨 조합(1~5등)이 당첨 기록(winning_verification.is_public)에도 공개되고,
 *   숨김·반려되면 함께 비공개가 된다. 건별 수동 조절은 API-27(AdminWinningRecordService).
 *
 * ⚠ 아직 하지 않은 것: 관리자 로그인이 없어서 "누가 처리했는지"(admin_action_log)는 남기지 않는다.
 *    관리자 로그인(세션)을 만들 때 이 서비스의 review() 에 작업 기록을 붙인다.
 * ⚠ 사진을 지우지 않는다. (반려 후에도 관리자가 사유를 확인할 수 있게 둔다. 보관 기간 정책은 따로 정한다)
 */
@Service
public class AdminVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminVerificationService.class);

    static final int MAX_PAGE_SIZE = 50;
    static final int REASON_MAX = 500;

    /** 현재 상태 → 이동할 수 있는 상태 */
    private static final Map<ReviewStatus, Set<ReviewStatus>> ALLOWED = Map.of(
            ReviewStatus.PENDING, Set.of(ReviewStatus.APPROVED, ReviewStatus.REJECTED),
            ReviewStatus.APPROVED, Set.of(ReviewStatus.HIDDEN, ReviewStatus.REJECTED),
            ReviewStatus.HIDDEN, Set.of(ReviewStatus.APPROVED, ReviewStatus.REJECTED),
            ReviewStatus.REJECTED, Set.of());

    private final AnonymousVerificationRepository proofRepo;
    private final NumberIssueRepository issueRepo;
    private final WinningVerificationRepository winningRepo;

    public AdminVerificationService(AnonymousVerificationRepository proofRepo, NumberIssueRepository issueRepo,
                                    WinningVerificationRepository winningRepo) {
        this.proofRepo = proofRepo;
        this.issueRepo = issueRepo;
        this.winningRepo = winningRepo;
    }

    /** @param status null 이면 전체. 최신순(id 큰 것부터) */
    @Transactional(readOnly = true)
    public AdminVerificationListResponse list(ReviewStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "페이지 값을 확인해 주세요.",
                    "page=" + page + ", size=" + size);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<AnonymousVerification> result = status == null
                ? proofRepo.findAll(pageable)
                : proofRepo.findByReviewStatus(status, pageable);

        List<Long> issueIds = result.getContent().stream().map(AnonymousVerification::getNumberIssueId).distinct().toList();
        Map<Long, Integer> bestRanks = new HashMap<>();
        Map<Long, Integer> rounds = new HashMap<>();
        if (!issueIds.isEmpty()) {
            for (Object[] row : proofRepo.findBestRanksByIssueIds(issueIds)) {
                bestRanks.put((Long) row[0], (Integer) row[1]);
            }
            for (NumberIssue n : issueRepo.findAllById(issueIds)) {
                rounds.put(n.getId(), n.getTargetRoundNo());
            }
        }

        List<AdminVerificationItem> items = result.getContent().stream()
                .map(v -> new AdminVerificationItem(v.getId(), v.getIssueCode(),
                        rounds.getOrDefault(v.getNumberIssueId(), 0), bestRanks.get(v.getNumberIssueId()),
                        v.getComment(), v.getReviewStatus().name(), v.isPublicVisible(), v.getAdminComment(),
                        v.getCreatedAt(), v.getReviewedAt(),
                        "/api/admin/verifications/" + v.getId() + "/photo"))
                .toList();
        return new AdminVerificationListResponse(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public VerificationReviewResponse review(Long id, VerificationReviewRequest request) {
        ReviewStatus to = request.reviewStatus();
        if (to == null || to == ReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "승인·반려·숨김 중에서 선택해 주세요.", "대상 상태=" + to);
        }
        String reason = request.adminComment() == null ? null : request.adminComment().trim();
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
        if (reason != null && reason.length() > REASON_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "코멘트는 " + REASON_MAX + "자까지 쓸 수 있어요.",
                    "코멘트 길이 초과: " + reason.length());
        }
        if ((to == ReviewStatus.REJECTED || to == ReviewStatus.HIDDEN) && reason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반려·숨김은 사유를 적어 주세요.", "사유 없음: " + to);
        }

        AnonymousVerification v = proofRepo.findById(id).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "인증을 찾을 수 없어요.", "verificationId=" + id));
        ReviewStatus from = v.getReviewStatus();
        if (!ALLOWED.get(from).contains(to)) {
            throw new BusinessException(ErrorCode.CONFLICT, "현재 상태(" + from + ")에서는 " + to + "(으)로 바꿀 수 없어요.",
                    "상태 이동 불가: " + from + " → " + to);
        }

        v.review(to, reason, LocalDateTime.now());
        proofRepo.saveAndFlush(v);
        // 승인이면 이 발급 건의 당첨 조합을 당첨 기록에 공개하고, 숨김·반려면 내린다. (같은 트랜잭션이라 함께 반영/취소된다)
        // ⚠ 이 줄 이후에는 영속성 컨텍스트가 비워지므로(clearAutomatically) v 를 다시 저장하지 않는다.
        int changed = winningRepo.updatePublicByIssue(v.getNumberIssueId(), v.isPublicVisible());
        log.info("인증 검수: verificationId={}, {} → {}, 당첨 기록 공개 변경 {}건", id, from, to, changed);
        return new VerificationReviewResponse(v.getId(), v.getReviewStatus().name(), v.isPublicVisible(), v.getReviewedAt());
    }
}
