package com.fortunaball.draw.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.dto.DrawResponse;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.draw.repository.DrawRoundRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회차 조회 서비스 (API-01 최신 회차, API-02 회차별 조회).
 * 이미 DB에 저장된 회차 데이터를 읽기만 한다. (수집은 DrawCollectService 담당)
 *
 * 캐시 (요구사항 BE-10)
 *  - 최신 회차("drawLatest"), 회차별("drawByRound")을 서버 메모리에 담는다.
 *  - 오류(없는 회차 등)는 캐시에 담기지 않는다. (예외가 나면 Spring 이 저장하지 않음)
 *  - 새 회차 저장·재수집 때는 DrawCacheEvictor 가 비운다.
 *
 * 오류 규칙
 *  - 회차 번호가 1 미만        → COMMON-001 (입력값 오류, 400)
 *  - 해당 회차 데이터가 없음   → COMMON-002 (없음, 404)
 *  - DB에 회차가 하나도 없음   → COMMON-002 (404)
 */
@Service
@Transactional(readOnly = true)
public class DrawQueryService {

    /** 캐시 이름. DrawCacheEvictor 도 같은 이름을 쓴다. */
    public static final String CACHE_LATEST = "drawLatest";
    public static final String CACHE_BY_ROUND = "drawByRound";

    private final DrawRoundRepository roundRepository;
    private final DrawPrizeRepository prizeRepository;

    public DrawQueryService(DrawRoundRepository roundRepository, DrawPrizeRepository prizeRepository) {
        this.roundRepository = roundRepository;
        this.prizeRepository = prizeRepository;
    }

    /** API-01: 가장 큰 회차 번호의 데이터 */
    @Cacheable(cacheNames = CACHE_LATEST, key = "'latest'")
    public DrawResponse getLatest() {
        DrawRound round = roundRepository.findTopByOrderByRoundNoDesc()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND, "아직 등록된 회차가 없어요.", "draw_round 테이블이 비어 있음"));
        return toResponse(round);
    }

    /** API-02: 회차 번호로 조회 */
    @Cacheable(cacheNames = CACHE_BY_ROUND, key = "#roundNo")
    public DrawResponse getByRound(int roundNo) {
        if (roundNo < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "회차 번호는 1 이상이어야 해요.", "roundNo=" + roundNo);
        }
        DrawRound round = roundRepository.findByRoundNo(roundNo)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND, "해당 회차 정보를 찾을 수 없어요.", "roundNo=" + roundNo));
        return toResponse(round);
    }

    private DrawResponse toResponse(DrawRound round) {
        List<DrawPrize> prizes = prizeRepository.findByRoundNoOrderByWinningRankAsc(round.getRoundNo());
        return DrawResponse.of(round, prizes);
    }
}
