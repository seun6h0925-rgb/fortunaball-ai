package com.fortunaball.admin.store.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 업로드한 판매점 파일을 반영 전까지 서버 폴더에 보관한다. (파일 이름은 업로드 번호.csv)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 비교 결과(API-49)와 반영(API-50)은 이 파일을 "다시 읽어서" 계산한다. 그래서 반영 전에 파일이 지워지면 반영할 수 없다.
 *  - 폴더는 설정 fortuna.store.upload-dir (기본 ./data/store-uploads, 서버를 켠 위치 기준)이다. 이 폴더는 Git 에 올리지 않는다.
 *  - 파일 이름은 서버가 만든 숫자만 쓴다. 사용자가 보낸 파일명은 경로에 쓰지 않는다. (경로 조작 방지)
 */
@Component
public class StoreUploadFiles {

    private static final Logger log = LoggerFactory.getLogger(StoreUploadFiles.class);

    private final Path dir;

    public StoreUploadFiles(@Value("${fortuna.store.upload-dir:./data/store-uploads}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public void save(long uploadId, byte[] bytes) throws IOException {
        Files.createDirectories(dir);
        Files.write(path(uploadId), bytes);
    }

    /** 파일이 없으면 IOException (NoSuchFileException) */
    public byte[] read(long uploadId) throws IOException {
        return Files.readAllBytes(path(uploadId));
    }

    /** 지우지 못해도 오류를 내지 않는다. (업무에는 영향 없음, 로그만 남김) */
    public void delete(long uploadId) {
        try {
            Files.deleteIfExists(path(uploadId));
        } catch (IOException e) {
            log.warn("[판매점업로드] 파일 삭제 실패: id={} / {}", uploadId, e.toString());
        }
    }

    private Path path(long uploadId) {
        return dir.resolve(uploadId + ".csv");
    }
}
