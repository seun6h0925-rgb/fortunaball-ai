package com.fortunaball.issue.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueCodeGeneratorTest {

    @Test
    void 열두자_대문자와_숫자이고_헷갈리는_글자가_없다() {
        for (int i = 0; i < 1000; i++) {
            String code = IssueCodeGenerator.next();
            assertEquals(12, code.length());
            assertTrue(code.matches("[A-Z2-9]{12}"), code);
            assertTrue(!code.matches(".*[ILO01].*"), "헷갈리는 글자 포함: " + code);
        }
    }

    @Test
    void 천번_만들어도_겹치지_않는다() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(IssueCodeGenerator.next());
        }
        assertEquals(1000, codes.size());
    }
}
