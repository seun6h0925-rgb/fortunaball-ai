package com.fortunaball.round.service;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * "지금 번호를 만들면 몇 회차 대상인가"를 정하는 규칙. (날짜만으로 계산하고 DB 는 보지 않는다)
 * 📚 배운 내용: @Component - 스프링이 객체를 하나 만들어 두고 필요한 곳에 넣어준다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 1회차는 2002-12-07(토). 1주일마다 1회차씩 늘어난다. (1246회 = 2026-10-17)
 *  - 토요일 20:00:00(서울 시간)이 되면 그 주 회차가 닫히고 "다음 회차"가 대상이 된다.
 *    예) 토 19:59:59 → 이번 주 회차 / 토 20:00:00 → 다음 주 회차
 *  - 회차 수집 스케줄러와 완전히 별개다. 수집이 늦어져도 대상 회차는 시간만 보고 바뀐다.
 *  - 화면에서 보낸 회차는 믿지 않는다. 발급 확정 때도 이 클래스로 다시 계산해서 맞는지 확인한다.
 */
@Component
public class RoundPolicy {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 1회차 추첨일 */
    private static final LocalDate FIRST_DRAW_DATE = LocalDate.of(2002, 12, 7);

    /** 이 시각부터 다음 회차가 대상이 된다 (추첨일 토요일 20:00:00) */
    private static final LocalTime SWITCH_TIME = LocalTime.of(20, 0, 0);

    /** 지금 시각 기준 대상 회차 */
    public int targetRound() {
        return targetRound(ZonedDateTime.now(ZONE));
    }

    /** 지정한 시각 기준 대상 회차 (시험·검증용으로 시각을 직접 넣을 수 있다) */
    public int targetRound(ZonedDateTime now) {
        return roundOf(drawDate(now));
    }

    /** 대상 회차의 추첨 예정일(토요일) */
    public LocalDate drawDate(ZonedDateTime now) {
        ZonedDateTime seoul = now.withZoneSameInstant(ZONE);
        LocalDate date = seoul.toLocalDate();
        int daysToSaturday = (DayOfWeek.SATURDAY.getValue() - date.getDayOfWeek().getValue() + 7) % 7;
        LocalDate saturday = date.plusDays(daysToSaturday);
        // 토요일 당일이고 전환 시각이 지났다면 다음 주 토요일 회차가 대상
        if (daysToSaturday == 0 && !seoul.toLocalTime().isBefore(SWITCH_TIME)) {
            saturday = saturday.plusWeeks(1);
        }
        return saturday;
    }

    /** 다음 회차로 바뀌는 시각 (= 대상 회차 추첨일의 20:00) */
    public LocalDateTime nextSwitchAt(ZonedDateTime now) {
        return drawDate(now).atTime(SWITCH_TIME);
    }

    /** 추첨일(토요일) → 회차 번호 */
    private int roundOf(LocalDate drawDate) {
        long weeks = ChronoUnit.WEEKS.between(FIRST_DRAW_DATE, drawDate);
        return (int) weeks + 1;
    }
}
