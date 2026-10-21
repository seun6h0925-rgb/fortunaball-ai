package com.fortunaball.like.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 서버를 켤 때 좋아요 표 2개가 없으면 만든다. (docs/db/schema.sql 에도 같은 내용이 있다)
 * 표가 이미 있으면 아무것도 하지 않는다. 만들기에 실패해도 서버는 계속 켜진다.
 */
@Component
public class LikeSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LikeSchemaInitializer.class);

    private final JdbcTemplate jdbc;

    public LikeSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS service_like_total ("
                    + "id TINYINT NOT NULL, total_count BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id)"
                    + ") ENGINE=InnoDB COMMENT='서비스 좋아요 전체 수(1행)'");
            jdbc.execute("CREATE TABLE IF NOT EXISTS service_like_daily ("
                    + "like_date DATE NOT NULL, actor_key VARCHAR(80) NOT NULL, click_count INT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (like_date, actor_key)"
                    + ") ENGINE=InnoDB COMMENT='좋아요 하루·사람별 누른 횟수(G:토큰해시, I:접속지해시)'");
        } catch (RuntimeException e) {
            log.error("좋아요 표를 만들지 못했습니다. docs/db/schema.sql 의 좋아요 부분을 직접 실행해 주세요.", e);
        }
    }
}
