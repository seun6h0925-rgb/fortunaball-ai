package com.fortunaball.draw.repository;

import com.fortunaball.draw.domain.ApiErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * api_error_log 조회·저장.
 * 관리자 오류 현황(대시보드, 로그 조회)이 최근 오류를 볼 때 findTop20ByOrderByCreatedAtDesc 를 쓴다.
 */
public interface ApiErrorLogRepository extends JpaRepository<ApiErrorLog, Long> {

    /** 가장 최근 오류 20건 */
    List<ApiErrorLog> findTop20ByOrderByCreatedAtDesc();
}
