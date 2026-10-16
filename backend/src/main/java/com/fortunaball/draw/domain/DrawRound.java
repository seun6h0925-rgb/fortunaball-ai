package com.fortunaball.draw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * 회차별 당첨번호 (테이블 draw_round). 추첨이 끝난 회차 한 줄 = 객체 하나.
 * 📚 배운 내용: JPA Entity - 테이블 한 줄 = 객체 하나로 연결한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - DB 에는 번호가 number_1 ~ number_6 컬럼 6개로 나뉘어 있고, 테이블 제약(CHECK)이 "오름차순, 1~45" 를 강제한다.
 *    그래서 이 클래스의 생성자가 먼저 검증하고 오름차순으로 정렬한 뒤 담는다 (잘못된 값이 DB 까지 가지 않게).
 *  - 번호 6개가 필요할 때는 getNumbers() 를 쓴다 (오름차순 리스트).
 *  - number1 ~ number6 은 @Column(name = "number_1") 처럼 DB 칼럼 이름을 직접 적었다. 스프링의 자동 이름 변환은
 *    camelCase 를 snake_case 로 바꿔 주지만 글자 뒤의 숫자 앞에는 밑줄을 넣어주지 않아서(number1 → number1),
 *    직접 적지 않으면 "Unknown column 'number1'" 오류가 난다. (2026-10-16 수집 중 실제로 난 오류)
 *  - created_at, updated_at 은 DB 가 자동으로 채우므로 여기에는 두지 않았다.
 */
@Entity
@Table(name = "draw_round")
public class DrawRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int roundNo;
    private LocalDate drawDate;
    @Column(name = "number_1")
    private int number1;
    @Column(name = "number_2")
    private int number2;
    @Column(name = "number_3")
    private int number3;
    @Column(name = "number_4")
    private int number4;
    @Column(name = "number_5")
    private int number5;
    @Column(name = "number_6")
    private int number6;
    private int bonusNumber;
    private String dataSource;   // 데이터 출처 (어디서 가져왔는지)

    protected DrawRound() { }   // JPA 가 객체를 만들 때 쓰는 기본 생성자

    /** 수집기가 새 회차를 저장할 때 쓴다. numbers 는 순서가 섞여 있어도 되고, 여기서 정렬한다. */
    public DrawRound(int roundNo, LocalDate drawDate, List<Integer> numbers, int bonusNumber, String dataSource) {
        if (roundNo < 1) {
            throw new IllegalArgumentException("회차 번호는 1 이상이어야 합니다: " + roundNo);
        }
        if (numbers == null || numbers.size() != 6 || new HashSet<>(numbers).size() != 6) {
            throw new IllegalArgumentException("당첨번호는 중복 없는 6개여야 합니다: " + numbers);
        }
        for (int n : numbers) {
            checkRange(n);
        }
        checkRange(bonusNumber);
        if (numbers.contains(bonusNumber)) {
            throw new IllegalArgumentException("보너스 번호가 당첨번호와 겹칩니다: " + bonusNumber);
        }
        List<Integer> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);

        this.roundNo = roundNo;
        this.drawDate = drawDate;
        this.number1 = sorted.get(0);
        this.number2 = sorted.get(1);
        this.number3 = sorted.get(2);
        this.number4 = sorted.get(3);
        this.number5 = sorted.get(4);
        this.number6 = sorted.get(5);
        this.bonusNumber = bonusNumber;
        this.dataSource = dataSource;
    }

    private static void checkRange(int n) {
        if (n < 1 || n > 45) {
            throw new IllegalArgumentException("번호는 1~45 여야 합니다: " + n);
        }
    }

    /** 당첨번호 6개 (오름차순) */
    public List<Integer> getNumbers() {
        return List.of(number1, number2, number3, number4, number5, number6);
    }

    public Long getId() { return id; }
    public int getRoundNo() { return roundNo; }
    public LocalDate getDrawDate() { return drawDate; }
    public int getBonusNumber() { return bonusNumber; }
    public String getDataSource() { return dataSource; }
}
