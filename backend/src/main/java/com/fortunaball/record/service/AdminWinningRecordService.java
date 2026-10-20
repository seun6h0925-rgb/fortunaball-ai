package com.fortunaball.record.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.record.dto.WinningRecordPublicResponse;
import com.fortunaball.verify.domain.WinningVerification;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 당첨 기록 공개 설정 (API-27, 관리자). 당첨 기록 한 건을 공개/비공개로 바꾼다.
 * 인증 승인·숨김은 발급 건 단위로 자동 반영되고(AdminVerificationService), 이 API 는 건별 수동 조절용이다.
 * ⚠ 낙첨 건(등수 없음)은 공개할 수 없다. 같은 값으로 다시 요청해도 오류 없이 그대로 성공한다.
 * ⚠ 관리자 로그인이 생기기 전이라 누가 바꿨는지는 아직 남기지 않는다. (admin_action_log 는 관리자 로그인 단계에서 붙인다)
 */
@Service
public class AdminWinningRecordService {

    private static final Logger log = LoggerFactory.getLogger(AdminWinningRecordService.class);

    private final WinningVerificationRepository winningRepo;

    public AdminWinningRecordService(WinningVerificationRepository winningRepo) {
        this.winningRepo = winningRepo;
    }

    @Transactional
    public WinningRecordPublicResponse setPublic(Long recordId, boolean publicVisible) {
        WinningVerification w = winningRepo.findById(recordId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "당첨 기록을 찾을 수 없어요.", "recordId=" + recordId));
        if (w.getWinningRank() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "낙첨 기록은 공개할 수 없어요.", "낙첨 기록: recordId=" + recordId);
        }
        winningRepo.updatePublicById(recordId, publicVisible);
        log.info("당첨 기록 공개 설정: recordId={}, public={}", recordId, publicVisible);
        return new WinningRecordPublicResponse(recordId, publicVisible);
    }
}
