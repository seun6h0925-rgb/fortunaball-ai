package com.fortunaball.store.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 서버를 켤 때 판매점 CSV 를 한 번 가져오는 실행기.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 설정 fortuna.store.import.path 가 있을 때만 만들어진다. 평소(설정 없음)에는 아무 일도 하지 않는다.
 *  - 쓰는 법: IntelliJ 실행 설정 → Program arguments 에
 *      --fortuna.store.import.path=C:\myws\semi_project\fortunaball-ai\docs\db\winning_stores_262_1246.csv
 *    를 넣고 서버를 켠다. 로그에 "판매점 CSV 가져오기 완료"가 나오면 끝이다. 그 뒤에는 이 인자를 지워 둔다.
 *  - 실패해도 서버는 꺼지지 않는다. 대신 로그에 원인을 남긴다 (실패하면 DB 에는 아무것도 저장되지 않는다).
 *  - 나중에 관리자 업로드 기능(API-30, 50)을 만들면 같은 StoreImportService 를 재사용할 수 있다.
 */
@Component
@ConditionalOnProperty(name = "fortuna.store.import.path")
public class StoreImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StoreImportRunner.class);

    private final StoreImportService service;
    private final String path;

    public StoreImportRunner(StoreImportService service, @Value("${fortuna.store.import.path}") String path) {
        this.service = service;
        this.path = path;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            log.error("판매점 CSV 가져오기 실패: 파일을 찾을 수 없습니다. path={}", path);
            return;
        }
        log.info("판매점 CSV 가져오기 시작: {}", file);
        try {
            StoreImportService.Result r = service.importFrom(file);
            log.info("판매점 CSV 가져오기 완료: 판매점 {}곳(폐점 {}곳), 당첨 이력 {}건, 집계 {}건, 회차 {}~{}, 기준일 {}",
                    r.stores(), r.closedStores(), r.histories(), r.summaries(), r.minRound(), r.maxRound(), r.baseDate());
        } catch (Exception e) {
            log.error("판매점 CSV 가져오기 실패 (저장된 내용 없음): {}", e.getMessage(), e);
        }
    }
}
