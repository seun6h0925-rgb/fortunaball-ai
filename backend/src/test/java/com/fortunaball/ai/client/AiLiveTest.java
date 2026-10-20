package com.fortunaball.ai.client;

import com.fortunaball.ai.AiTestFixtures;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 진짜 OpenAI 를 딱 1번 불러 보는 확인용 테스트. 평소 전체 테스트에서는 건너뛴다. (돈이 거의 안 들지만 0은 아니다)
 *
 * 켜는 법 (PowerShell, backend 폴더):
 *   $env:AI_LIVE_TEST="1"; .\gradlew test --tests "*AiLiveTest"; Remove-Item Env:AI_LIVE_TEST
 *   결과 확인: Get-Content build\ai-live-result.txt
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 키는 저장소 루트의 .env 파일에서 읽는다. (터미널에 키를 직접 치지 않게 하려는 것)
 *  - 결과 파일에는 모델 이름·토큰 수·시간·짧은 답만 적는다. 키는 적지 않는다.
 *  - 실패하면 결과 파일에 오류 코드와 상세(HTTP 상태, error type/code)가 적힌다. 모델 이름이 틀렸을 때도 여기서 알 수 있다.
 *  - 환경 변수 AI_LIVE_MODEL 을 주면 그 모델로 불러 본다. (없으면 application.yml 과 같은 기본 모델)
 */
class AiLiveTest {

    private static final Path RESULT = Path.of("build", "ai-live-result.txt");

    @Test
    void 실제_OpenAI를_한_번_불러_본다() throws IOException {
        Assumptions.assumeTrue("1".equals(System.getenv("AI_LIVE_TEST")), "AI_LIVE_TEST=1 일 때만 실행");
        String key = readKeyFromDotEnv();
        Assumptions.assumeTrue(key != null && !key.isBlank(), ".env 에서 AI_API_KEY 를 찾지 못함");

        String model = System.getenv("AI_LIVE_MODEL");
        AiProperties props = (model == null || model.isBlank())
                ? AiTestFixtures.props(key)
                : AiTestFixtures.propsWithModels(key, model, model);
        OpenAiClient client = new OpenAiClient(props);

        long start = System.nanoTime();
        String report;
        boolean ok = false;
        try {
            AiResponse response = client.complete(new AiRequest(AiRole.ANSWER,
                    "너는 짧게 한국어로 답한다.", "숫자 1부터 3까지를 쉼표로만 나열해 줘.", 300));
            ok = true;
            report = "성공\n모델: " + response.modelName()
                    + "\n입력 토큰: " + response.inputTokens()
                    + "\n출력 토큰: " + response.outputTokens()
                    + "\n시간(ms): " + (System.nanoTime() - start) / 1_000_000
                    + "\n답: " + response.text();
        } catch (RuntimeException e) {
            report = "실패\n오류: " + e.getClass().getSimpleName() + " / " + e.getMessage()
                    + (e instanceof com.fortunaball.common.exception.BusinessException be
                    ? "\n상세: " + be.getDetail() + "\n코드: " + be.getErrorCode().getCode() : "")
                    + "\n요청 모델: " + props.answerModel();
        }
        Files.createDirectories(RESULT.getParent());
        Files.writeString(RESULT, report + "\n", StandardCharsets.UTF_8);
        assertTrue(ok, "실제 호출 실패. build/ai-live-result.txt 를 확인하세요.");
    }

    /**
     * 저장소 루트(../.env) 또는 backend/.env 에서 AI_API_KEY 값을 읽는다. 값은 화면에 찍지 않는다.
     * .env 의 한글 주석이 UTF-8 이 아닐 수 있어서 ISO_8859_1 로 읽는다. (이 방식은 어떤 글자가 있어도 읽기가 깨지지 않고,
     * 영문·숫자로 된 키는 그대로 읽힌다)
     */
    private static String readKeyFromDotEnv() throws IOException {
        for (Path candidate : List.of(Path.of("..", ".env"), Path.of(".env"))) {
            if (!Files.exists(candidate)) {
                continue;
            }
            for (String line : Files.readAllLines(candidate, StandardCharsets.ISO_8859_1)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("AI_API_KEY=")) {
                    String value = trimmed.substring("AI_API_KEY=".length()).trim();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\""))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        }
        return null;
    }
}
