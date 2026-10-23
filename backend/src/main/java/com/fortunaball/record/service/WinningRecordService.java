package com.fortunaball.record.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import com.fortunaball.record.dto.WinningRecordItem;
import com.fortunaball.record.dto.WinningRecordListResponse;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import com.fortunaball.verify.domain.WinningVerification;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 우리 서비스 당첨 기록 조회 (API-17, 공개용).
 * 📚 배운 내용: @Service, @Transactional(readOnly)
 *
 * 공개된(is_public=1) 1~5등 당첨 건만 보여 준다. 정렬: 회차 최신순 → 등수 높은 순 → 최신 등록순.
 * 사용자 한마디는 같은 발급 건의 "승인 + 공개" 인증에서 가져온다. (사진은 내려주지 않는다)
 * 발급코드·발급 시각(초 단위)·게스트(앞 4자리만)는 공개한다. 이용자가 "이 코드로 이렇게 인증하는구나"를 볼 수 있게 하려는 것.
 */
@Service
public class WinningRecordService {

    static final int MAX_PAGE_SIZE = 50;

    private final WinningVerificationRepository winningRepo;
    private final IssueNumberRepository numberRepo;
    private final NumberIssueRepository issueRepo;
    private final AnonymousVerificationRepository proofRepo;

    public WinningRecordService(WinningVerificationRepository winningRepo, IssueNumberRepository numberRepo,
                                NumberIssueRepository issueRepo, AnonymousVerificationRepository proofRepo) {
        this.winningRepo = winningRepo;
        this.numberRepo = numberRepo;
        this.issueRepo = issueRepo;
        this.proofRepo = proofRepo;
    }

    /** @param roundNo 회차(비우면 전체) @param rank 등수 1~5(비우면 전체) */
    @Transactional(readOnly = true)
    public WinningRecordListResponse list(Integer roundNo, Integer rank, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "페이지 값을 확인해 주세요.", "page=" + page + ", size=" + size);
        }
        if (roundNo != null && roundNo < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회차를 확인해 주세요.", "roundNo=" + roundNo);
        }
        if (rank != null && (rank < 1 || rank > 5)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "등수는 1~5등만 조회할 수 있어요.", "rank=" + rank);
        }
        Sort sort = Sort.by(Sort.Order.desc("roundNo"), Sort.Order.asc("winningRank"), Sort.Order.desc("id"));
        Page<WinningVerification> result = winningRepo.findPublicRecords(roundNo, rank, PageRequest.of(page, size, sort));

        // 한마디 붙이기: 당첨 건 → 발급 번호 → 발급 건 → 공개 중인 인증 (쿼리 2번으로 한 쪽 전체 처리)
        Map<Long, String> commentByIssue = new HashMap<>();
        Map<Long, Long> issueByNumber = new HashMap<>();
        Map<Long, NumberIssue> issueById = new HashMap<>();
        if (!result.isEmpty()) {
            List<Long> numberIds = result.getContent().stream().map(WinningVerification::getIssueNumberId).distinct().toList();
            for (IssueNumber n : numberRepo.findAllById(numberIds)) {
                issueByNumber.put(n.getId(), n.getNumberIssueId());
            }
            List<Long> issueIds = issueByNumber.values().stream().distinct().toList();
            for (NumberIssue ni : issueRepo.findAllById(issueIds)) {
                issueById.put(ni.getId(), ni);
            }
            for (AnonymousVerification v : proofRepo.findByNumberIssueIdInAndReviewStatusAndPublicVisibleTrue(
                    issueIds, ReviewStatus.APPROVED)) {
                if (v.getComment() != null) {
                    commentByIssue.put(v.getNumberIssueId(), v.getComment());
                }
            }
        }

        List<WinningRecordItem> items = result.getContent().stream()
                .map(w -> {
                    Long issueId = issueByNumber.get(w.getIssueNumberId());
                    NumberIssue ni = issueById.get(issueId);
                    return new WinningRecordItem(w.getId(), w.getRoundNo(), w.getWinningRank(), w.getMatchCount(),
                            w.isBonusMatched(), ni == null ? null : ni.getIssueCode(),
                            ni == null ? null : maskGuest(ni.getGuestToken()),
                            ni == null ? null : ni.getCreatedAt(), commentByIssue.get(issueId), w.getVerifiedAt());
                })
                .toList();
        return new WinningRecordListResponse(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    /** 공개용 게스트 표기: "guest" + 앞 4자리 + "****" (예: guest3f2a****). 짧은 값이면 전부 가린다. */
    static String maskGuest(String token) {
        if (token == null || token.length() < 4) {
            return "guest****";
        }
        return "guest" + token.substring(0, 4) + "****";
    }
}
