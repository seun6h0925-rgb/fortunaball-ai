package com.fortunaball.verify.repository;

import com.fortunaball.issue.domain.IssueNumber;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 당첨 검증 배치 전용 조회: "추첨이 끝났는데 아직 당첨 판정이 없는 발급 게임"을 찾는다.
 * 읽기만 하는 인터페이스다. (판정 저장은 WinningVerificationRepository.insertIfAbsent)
 * 📚 배운 내용: Spring Data 의 Repository 상속 + 직접 쓴 SQL(@Query nativeQuery) + 인터페이스 프로젝션(필요한 컬럼만 받기)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - draw_round 와 JOIN 하므로 "추첨 결과가 저장된 회차"의 발급 게임만 나온다. 추첨 전 회차는 자연스럽게 빠진다.
 *  - winning_verification 에 이미 줄이 있는 게임(이전 조회·이전 배치로 판정됨)은 나오지 않는다.
 *  - 컬럼 별칭(AS id, AS roundNo, AS number1 …)이 아래 인터페이스의 getter 이름과 같아야 값이 들어온다.
 */
public interface UnverifiedGameRepository extends Repository<IssueNumber, Long> {

    /** 판정이 필요한 발급 게임 한 줄 */
    interface UnverifiedGame {
        Long getId();

        Integer getRoundNo();

        Integer getNumber1();

        Integer getNumber2();

        Integer getNumber3();

        Integer getNumber4();

        Integer getNumber5();

        Integer getNumber6();
    }

    /** 오래된 발급부터 limit 개. 한 번에 너무 많이 읽지 않으려고 나눠서 가져온다. */
    @Query(nativeQuery = true, value = """
            SELECT n.id AS id, i.target_round_no AS roundNo,
                   n.number_1 AS number1, n.number_2 AS number2, n.number_3 AS number3,
                   n.number_4 AS number4, n.number_5 AS number5, n.number_6 AS number6
              FROM issue_number n
              JOIN number_issue i ON i.id = n.number_issue_id
              JOIN draw_round d ON d.round_no = i.target_round_no
              LEFT JOIN winning_verification v ON v.issue_number_id = n.id
             WHERE v.id IS NULL
             ORDER BY n.id
             LIMIT :limit
            """)
    List<UnverifiedGame> findUnverified(@Param("limit") int limit);
}
