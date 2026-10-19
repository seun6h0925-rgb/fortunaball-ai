package com.fortunaball.generate.domain;

import com.fortunaball.stat.domain.PatternCode;

import java.time.Instant;
import java.util.List;

/**
 * 번호 생성 임시 결과 한 건. (DB 에 저장하지 않고 서버 메모리에 30분만 보관한다)
 * 브라우저에는 confirmToken 만 보내고, 번호 내용은 서버가 들고 있다가 "발급 확정(API-04)" 때 이 토큰으로 꺼내 쓴다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - targetRound / basisRound 는 생성 시점에 서버가 계산한 값이다. 확정할 때 현재 대상 회차와 다르면(토요일 20:00 이후) 확정할 수 없다.
 *  - patternCode 는 패턴 없이 생성했으면 null 이다.
 */
public record GenerationTicket(
        String confirmToken,
        String guestToken,
        int targetRound,
        int basisRound,
        PatternCode patternCode,
        List<Combo> games,
        Instant createdAt,
        Instant expiresAt) {

    public GenerationTicket {
        games = List.copyOf(games);   // 밖에서 목록을 바꿔도 보관된 결과가 바뀌지 않게 복사해 둔다
    }
}
