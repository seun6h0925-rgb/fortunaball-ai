package com.fortunaball;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 포르투나볼 AI 백엔드 시작점.
 * 실행 전에 저장소 루트의 .env 파일에 DB 접속 값을 채워야 한다. (application.yml 참고)
 */
@SpringBootApplication
public class FortunaBallApplication {

    public static void main(String[] args) {
        SpringApplication.run(FortunaBallApplication.class, args);
    }
}
