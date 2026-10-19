package com.fortunaball.issue.service;

import java.security.SecureRandom;

/**
 * 발급 코드 만들기. 12자, 대문자와 숫자 (헷갈리는 0/O, 1/I/L 은 뺀다).
 * 추측하기 어렵도록 SecureRandom 을 쓴다. (요구사항: 10자 이상, 추측하기 어려운 영숫자)
 * 31가지 글자 12자리 = 약 7.9 × 10^17 가지. 중복되면 호출한 쪽(IssueService)이 다시 만든다.
 */
final class IssueCodeGenerator {

    static final int LENGTH = 12;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private IssueCodeGenerator() {
    }

    static String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
