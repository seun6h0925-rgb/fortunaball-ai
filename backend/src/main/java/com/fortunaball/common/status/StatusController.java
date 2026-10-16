package com.fortunaball.common.status;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.response.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서버·DB 연결 확인용 주소. 세팅이 잘 됐는지, 배포 후 살아있는지 볼 때 쓴다.
 *   GET /api/status  → 서버 시간과 DB 연결 결과
 * DB가 안 되면 503으로 응답하고, 원인은 로그(traceId)에서 확인한다.
 */
@RestController
public class StatusController {

    private final JdbcTemplate jdbcTemplate;

    public StatusController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/status")
    public ApiResponse<Map<String, Object>> status() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "데이터베이스에 연결할 수 없어요.",
                    "DB 연결 확인 실패: " + e.getClass().getSimpleName() + " / " + e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("server", "UP");
        data.put("db", "UP");
        data.put("time", OffsetDateTime.now(ZoneId.of("Asia/Seoul")).toString());
        return ApiResponse.ok(data);
    }
}
