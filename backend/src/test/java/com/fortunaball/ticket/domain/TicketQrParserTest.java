package com.fortunaball.ticket.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QR 글자 해석 확인. ⚠ 실제 용지의 TR 번호·일련번호는 쓰지 않고 가짜 값(1000000001 12345678)으로 만든 글자를 쓴다.
 * (GitHub 저장소가 퍼블릭이라 실제 용지 식별값을 올리지 않으려고)
 */
class TicketQrParserTest {

    private static final String FAKE_SERIAL = "100000000112345678";
    /** 실제 용지 구조와 같은 5줄(모두 수동) */
    private static final String FIVE_GAMES =
            "m030614163442m010305091442m041421223637m061416203145m121617262936";

    private static String qr(String host, String value) {
        return "http://" + host + "/?v=" + value;
    }

    private static InvalidTicketQrException reject(String text) {
        return assertThrows(InvalidTicketQrException.class, () -> TicketQrParser.parse(text));
    }

    @Test
    void 정상_용지는_회차와_다섯_줄의_번호가_나온다() {
        ParsedTicket t = TicketQrParser.parse(qr("qr.dhlottery.co.kr", "1239" + FIVE_GAMES + FAKE_SERIAL));

        assertEquals(1239, t.roundNo());
        assertEquals(5, t.gameCount());
        assertEquals(GameKind.MANUAL, t.games().get(0).kind());
        assertEquals(List.of(3, 6, 14, 16, 34, 42), t.games().get(0).numbers());     // A 줄
        assertEquals(List.of(12, 16, 17, 26, 29, 36), t.games().get(4).numbers());   // E 줄
        assertEquals(FAKE_SERIAL, t.serial());
    }

    @Test
    void 자동_반자동_수동이_섞여도_읽고_식별값이_없어도_된다() {
        ParsedTicket t = TicketQrParser.parse(qr("m.dhlottery.co.kr", "1240q010203040506s071819202122m232425262728"));

        assertEquals(1240, t.roundNo());
        assertEquals(List.of(GameKind.AUTO, GameKind.SEMI_AUTO, GameKind.MANUAL),
                t.games().stream().map(TicketGame::kind).toList());
        assertEquals("", t.serial());
    }

    @Test
    void 번호가_뒤섞여_있어도_오름차순으로_정렬해서_돌려준다() {
        ParsedTicket t = TicketQrParser.parse(qr("qr.dhlottery.co.kr", "5q452211030201"));
        assertEquals(List.of(1, 2, 3, 11, 22, 45), t.games().get(0).numbers());
    }

    @Test
    void 앞뒤_공백_https_대문자_호스트도_읽는다() {
        ParsedTicket t = TicketQrParser.parse("  https://QR.DHLOTTERY.CO.KR/?v=1239" + FIVE_GAMES + "  ");
        assertEquals(1239, t.roundNo());
    }

    @Test
    void 동행복권_주소가_아니면_거절한다_비슷한_주소도_거절() {
        reject(qr("example.com", "1239" + FIVE_GAMES));
        reject(qr("evildhlottery.co.kr", "1239" + FIVE_GAMES));
        reject(qr("dhlottery.co.kr.evil.com", "1239" + FIVE_GAMES));
    }

    @Test
    void 비었거나_너무_길거나_주소가_아니면_거절한다() {
        reject(null);
        reject("");
        reject("   ");
        reject("1239" + FIVE_GAMES);                                          // 주소 없이 값만
        reject("http://qr.dhlottery.co.kr/?x=1239" + FIVE_GAMES);             // v= 가 아님
        reject(qr("qr.dhlottery.co.kr", "1239" + FIVE_GAMES + "9".repeat(300)));
    }

    @Test
    void 게임이_없거나_여섯_줄_이상이면_거절한다() {
        reject(qr("qr.dhlottery.co.kr", "1239"));
        reject(qr("qr.dhlottery.co.kr", "1239106368072612810027"));
        reject(qr("qr.dhlottery.co.kr", "1239" + FIVE_GAMES + "m010203040506" + FAKE_SERIAL));
    }

    @Test
    void 모르는_글자_짧은_번호_회차_오류는_거절한다() {
        reject(qr("qr.dhlottery.co.kr", "1239x030614163442"));                // 모르는 선택 방식
        reject(qr("qr.dhlottery.co.kr", "1239m03061416344"));                 // 번호 자리가 모자람
        reject(qr("qr.dhlottery.co.kr", "0m030614163442"));                   // 회차 0
        reject(qr("qr.dhlottery.co.kr", "m030614163442"));                    // 회차 없음
    }

    @Test
    void 번호가_범위_밖이거나_한_줄에_중복이면_거절한다() {
        reject(qr("qr.dhlottery.co.kr", "1239m030614163446"));                // 46
        reject(qr("qr.dhlottery.co.kr", "1239m000614163442"));                // 00
        reject(qr("qr.dhlottery.co.kr", "1239m030614163403"));                // 03 중복
    }

    @Test
    void 오류_메시지에는_QR_원문이_들어가지_않는다() {
        String text = qr("qr.dhlottery.co.kr", "1239" + FIVE_GAMES + FAKE_SERIAL + "9".repeat(300));
        InvalidTicketQrException e = reject(text);
        assertEquals(false, e.getMessage().contains(FAKE_SERIAL));
        assertEquals(false, e.getMessage().contains("m030614163442"));
    }
}
