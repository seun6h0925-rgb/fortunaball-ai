package com.fortunaball.dream.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 꿈 내용에 직접 적힌 숫자(1~45)를 뽑는다. (한국 전통: 꿈에 나온 숫자를 번호로 쓴다)
 *  - 한 자리·두 자리 숫자만 본다. "2026"처럼 세 자리 이상 숫자의 일부는 뽑지 않는다.
 *  - 1~45 밖의 숫자와 중복은 버리고, 적힌 순서를 유지하며 최대 6개까지만 쓴다.
 *  - 숫자 앞의 0 은 허용하지 않는다. ("07"은 건너뛴다)
 */
@Component
public class DreamNumberExtractor {

    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])([1-9][0-9]?)(?![0-9])");

    public List<Integer> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<Integer> found = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find() && found.size() < 6) {
            int value = Integer.parseInt(m.group(1));
            if (value >= 1 && value <= 45) {
                found.add(value);
            }
        }
        return new ArrayList<>(found);
    }
}
