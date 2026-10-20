package com.fortunaball.dream.domain;

import java.util.List;

/**
 * 꿈 지식베이스에서 읽어 온 값들 (읽기 전용). DB 테이블 한 행 = 레코드 하나.
 * JPA 엔티티로 만들지 않은 이유: 꿈풀이는 읽기만 하고, 상징·별칭·해석·번호를 묶어서 한 번에 조회하는 모양이라
 * 조회 전용 SQL 이 더 단순하고 쓰기 코드(관리자 등록·수정)는 관리자 기능을 만들 때 따로 둔다.
 */
public final class DreamKnowledge {

    private DreamKnowledge() {
    }

    /** 상징 한 개와 그 별칭들. keyword 와 aliases 모두 매칭에 쓴다. */
    public record SymbolEntry(long id, String keyword, List<String> aliases) {
    }

    /** 꿈에서 찾은 상징 (등장 순서대로). */
    public record MatchedSymbol(long id, String keyword) {
    }

    /** 상황별 해석 한 행. originRegion 은 KR(한국 전통) 또는 GLOBAL. sourceName 은 출처가 없으면 null. */
    public record Interpretation(long symbolId, String situation, String summary, String polarity,
                                 String confidence, String originRegion, String sourceName) {
        public boolean isKorean() {
            return "KR".equals(originRegion);
        }
    }

    /** 상징의 추천 번호 한 개. */
    public record SymbolNumber(long symbolId, int number, DreamNumberOrigin origin) {
    }

    /** 상징에 연결된 출처 (화면 출처 표기용). */
    public record SourceRef(long symbolId, String name, String type, String trustLevel) {
    }

    /** 응답에 담는 최종 번호 한 개. */
    public record PickedNumber(int number, DreamNumberOrigin origin) {
    }
}
