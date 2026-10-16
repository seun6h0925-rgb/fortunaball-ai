package com.fortunaball.draw.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.dto.DrawApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * 동행복권 공식 사이트에서 "한 회차"의 당첨번호·당첨금을 가져오는 클래스. (가져오기만 하고 DB 저장은 하지 않는다)
 *   주소: https://www.dhlottery.co.kr/lt645/selectPstLt645Info.do?srchLtEpsd={회차}
 * 📚 배운 내용: @Component - 스프링이 객체를 하나 만들어 두고 필요한 곳에 넣어준다. (@Value 는 설정값을 읽는다)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 응답을 바로 객체로 바꾸지 않고, 먼저 "글자(String)"로 받은 뒤 직접 변환한다.
 *    사이트가 점검 중이거나 개편되면 JSON 대신 HTML 안내 화면이 오는데, 그때 어떤 내용이 왔는지 로그로 남기기 위해서다.
 *  - 결과가 비어 있으면(아직 추첨 전인 회차) 오류가 아니라 Optional.empty() 를 돌려준다.
 *  - 통신 실패·점검·형식 변경은 BusinessException(COMMON-008)으로 던진다. 화면에는 짧은 문구만 나가고,
 *    원인(회차, 상태코드, 받은 내용 앞부분)은 detail 로 로그(traceId)에 남는다.
 *  - 주소와 제한 시간은 application.yml 의 fortuna.draw.* 로 바꿀 수 있고, 없으면 아래 기본값을 쓴다.
 */
@Component
public class DrawCollector {

    private static final Logger log = LoggerFactory.getLogger(DrawCollector.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final Duration requestTimeout;

    public DrawCollector(
            ObjectMapper objectMapper,
            @Value("${fortuna.draw.api-url:https://www.dhlottery.co.kr/lt645/selectPstLt645Info.do}") String apiUrl,
            @Value("${fortuna.draw.timeout-ms:10000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.requestTimeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5000)))   // 연결은 최대 5초 안에
                .followRedirects(HttpClient.Redirect.NEVER)   // 다른 화면(로그인·점검 안내)으로 넘어가면 따라가지 않고 실패로 본다
                .build();
    }

    /**
     * 한 회차를 가져온다.
     * @return 회차 정보. 아직 데이터가 없는 회차(추첨 전)면 비어 있음.
     * @throws BusinessException 통신 실패, 200 이 아닌 응답, 형식이 달라진 응답
     */
    public Optional<DrawApiResponse.Item> fetch(int roundNo) {
        URI uri = URI.create(apiUrl + "?srchLtEpsd=" + roundNo);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (compatible; FortunaBall/0.1)")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 중단 요청은 지우지 않고 다시 표시해 둔다
            throw fail(roundNo, "요청이 중단됨", e);
        } catch (IOException e) {
            throw fail(roundNo, "통신 실패(연결·시간 초과)", e);
        }

        if (response.statusCode() != 200) {
            throw fail(roundNo, "응답 상태 " + response.statusCode() + " / 받은 내용: " + head(response.body()), null);
        }

        DrawApiResponse parsed;
        try {
            parsed = objectMapper.readValue(response.body(), DrawApiResponse.class);
        } catch (JsonProcessingException e) {
            // JSON 이 아닌 글자가 온 경우 (점검 안내 화면, 사이트 개편 등)
            throw fail(roundNo, "응답 형식이 달라짐 / 받은 내용: " + head(response.body()), e);
        }

        if (parsed == null || !parsed.hasData()) {
            log.info("[회차수집] {}회 데이터 없음 (아직 추첨 전이거나 없는 회차)", roundNo);
            return Optional.empty();
        }
        DrawApiResponse.Item item = parsed.first();
        if (item.ltEpsd() != roundNo) {
            // 다른 회차가 왔다면 그대로 저장하면 잘못된 데이터가 되므로 막는다
            throw fail(roundNo, "요청한 회차와 다른 회차가 옴: " + item.ltEpsd(), null);
        }
        return Optional.of(item);
    }

    private BusinessException fail(int roundNo, String reason, Exception cause) {
        String detail = "회차 수집 실패 round=" + roundNo + " / " + reason
                + (cause == null ? "" : " / " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "공식 회차 데이터를 가져오지 못했어요.", detail);
    }

    /** 로그가 너무 길어지지 않게 받은 내용의 앞 200자만 남긴다 (줄바꿈은 공백으로) */
    private static String head(String body) {
        if (body == null) {
            return "(없음)";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }
}
