package com.fortunaball.common.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진 업로드 설정. application.yml 의 fortuna.upload.* 를 읽는다.
 * 📚 배운 내용: @ConfigurationProperties
 *
 * @param dir       사진을 저장할 폴더. .env 의 UPLOAD_DIR 에서 온다. 프로젝트(Git) 밖의 절대 경로여야 한다.
 *                  ⚠ .env 에서는 역슬래시(\) 대신 슬래시(/)로 적는다. (예: C:/myws/semi_project/fortuna-data-uploads)
 * @param maxSizeMb 사진 1장 최대 용량(MB). 서버가 받는 한도(spring.servlet.multipart)도 이 값을 따라간다.
 */
@ConfigurationProperties(prefix = "fortuna.upload")
public record UploadProperties(
        @DefaultValue("") String dir,
        @DefaultValue("10") int maxSizeMb) {

    public long maxBytes() {
        return maxSizeMb * 1024L * 1024L;
    }
}
