package com.fortunaball.issue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.List;

/**
 * 발급된 번호 조합 한 게임. (number_issue 1건 아래에 게임 순서대로 여러 건)
 * DB 테이블: issue_number
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 번호는 반드시 오름차순 6개(1~45)여야 한다. DB 에도 같은 CHECK 가 있어서 어기면 저장이 거절된다.
 *  - comboKey 는 "03-08-18-27-33-41" 형식. "같은 조합이 몇 번 발급됐는지"(combo_issue_count)와 연결되는 값이다.
 *  - numberIssueId 는 연관관계(객체 참조) 대신 번호만 들고 있다. 이 프로젝트는 단순하게 id 로만 연결한다. (StatPatternCard 와 같은 방식)
 */
@Entity
@Table(name = "issue_number")
public class IssueNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "number_issue_id", nullable = false, updatable = false)
    private Long numberIssueId;

    @Column(name = "game_order", nullable = false, updatable = false)
    private int gameOrder;

    @Column(name = "combo_key", nullable = false, updatable = false, length = 17)
    private String comboKey;

    @Column(name = "number_1", nullable = false, updatable = false)
    private int number1;
    @Column(name = "number_2", nullable = false, updatable = false)
    private int number2;
    @Column(name = "number_3", nullable = false, updatable = false)
    private int number3;
    @Column(name = "number_4", nullable = false, updatable = false)
    private int number4;
    @Column(name = "number_5", nullable = false, updatable = false)
    private int number5;
    @Column(name = "number_6", nullable = false, updatable = false)
    private int number6;

    protected IssueNumber() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    /** @param numbers 오름차순 6개 (generate.domain.Combo 가 이미 검증해서 넘겨준다) */
    public static IssueNumber of(Long numberIssueId, int gameOrder, String comboKey, List<Integer> numbers) {
        if (numbers == null || numbers.size() != 6) {
            throw new IllegalArgumentException("번호는 6개여야 합니다: " + numbers);
        }
        IssueNumber n = new IssueNumber();
        n.numberIssueId = numberIssueId;
        n.gameOrder = gameOrder;
        n.comboKey = comboKey;
        n.number1 = numbers.get(0);
        n.number2 = numbers.get(1);
        n.number3 = numbers.get(2);
        n.number4 = numbers.get(3);
        n.number5 = numbers.get(4);
        n.number6 = numbers.get(5);
        return n;
    }

    public List<Integer> numbers() {
        return List.of(number1, number2, number3, number4, number5, number6);
    }

    public Long getId() { return id; }
    public Long getNumberIssueId() { return numberIssueId; }
    public int getGameOrder() { return gameOrder; }
    public String getComboKey() { return comboKey; }
}
