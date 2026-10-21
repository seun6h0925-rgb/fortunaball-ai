package com.fortunaball.store.importer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판매점 당첨 CSV 읽기. 파일 → StoreCsvRow 목록.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 파일 맨 앞에 BOM(보이지 않는 글자 U+FEFF)이 붙어 있다. 안 지우면 첫 열 이름이 "draw" 가 아니라 "﻿draw" 가 되어
 *    열을 못 찾는다. 그래서 읽자마자 지운다.
 *  - 열 위치가 아니라 "열 이름"으로 찾는다. 열 순서가 바뀌어도 읽을 수 있다. 필요한 열이 없으면 바로 오류를 낸다.
 *  - 값 앞뒤 공백은 지운다 (CSV 에 "   " 처럼 공백만 있는 칸이 많다). 공백만 있으면 빈 값("")이 된다.
 *  - 줄 하나가 잘못되면 "몇 번째 줄"인지 넣어서 IllegalArgumentException 을 던진다 (오류 추적용).
 */
public final class StoreCsvParser {

    private static final List<String> REQUIRED = List.of(
            "draw", "rank", "shop_id", "shop_name", "status", "region",
            "road_addr", "jibun_addr", "auto_manual", "lat", "lon");

    private StoreCsvParser() { }

    public static List<StoreCsvRow> parse(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return parseText(text);
    }

    /** 문자열 전체를 읽는다 (테스트에서 파일 없이 쓰기 위해 분리). */
    public static List<StoreCsvRow> parseText(String text) {
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);   // BOM 제거
        }
        List<List<String>> records = splitRecords(text);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("CSV 가 비어 있습니다.");
        }

        Map<String, Integer> col = new HashMap<>();
        List<String> header = records.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim(), i);
        }
        for (String name : REQUIRED) {
            if (!col.containsKey(name)) {
                throw new IllegalArgumentException("CSV 에 필요한 열이 없습니다: " + name);
            }
        }

        List<StoreCsvRow> rows = new ArrayList<>(records.size());
        for (int i = 1; i < records.size(); i++) {
            int lineNo = i + 1;   // 머리글이 1번째 줄
            List<String> r = records.get(i);
            try {
                rows.add(toRow(r, col));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("CSV " + lineNo + "번째 줄 오류: " + e.getMessage(), e);
            }
        }
        return rows;
    }

    private static StoreCsvRow toRow(List<String> r, Map<String, Integer> col) {
        int rank = Integer.parseInt(get(r, col, "rank"));
        if (rank != 1 && rank != 2) {
            throw new IllegalArgumentException("등수는 1 또는 2 여야 합니다: " + rank);
        }
        String shopCode = get(r, col, "shop_id");
        String name = get(r, col, "shop_name");
        if (shopCode.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("판매점 번호나 이름이 비어 있습니다.");
        }
        return new StoreCsvRow(
                Integer.parseInt(get(r, col, "draw")),
                rank,
                shopCode,
                name,
                "폐점".equals(get(r, col, "status")),
                get(r, col, "region"),
                get(r, col, "road_addr"),
                get(r, col, "jibun_addr"),
                toWinType(get(r, col, "auto_manual")),
                new BigDecimal(get(r, col, "lat")),
                new BigDecimal(get(r, col, "lon")));
    }

    /** CSV 의 한글 구분을 DB 값으로 바꾼다. 비어 있으면 null (구분 없음). */
    static String toWinType(String value) {
        return switch (value) {
            case "" -> null;
            case "자동" -> "AUTO";
            case "수동" -> "MANUAL";
            case "반자동" -> "SEMI_AUTO";
            default -> throw new IllegalArgumentException("알 수 없는 당첨 구분: " + value);
        };
    }

    private static String get(List<String> r, Map<String, Integer> col, String name) {
        int idx = col.get(name);
        return idx < r.size() ? r.get(idx).trim() : "";
    }

    /** 따옴표 안의 쉼표·줄바꿈을 지키면서 CSV 를 칸 목록으로 쪼갠다. 빈 줄은 건너뛴다. */
    private static List<List<String>> splitRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');   // "" 는 따옴표 글자 하나
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                endRecord(records, current, field);
                current = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            endRecord(records, current, field);
        }
        return records;
    }

    private static void endRecord(List<List<String>> records, List<String> current, StringBuilder field) {
        current.add(field.toString());
        field.setLength(0);
        boolean blankLine = current.size() == 1 && current.get(0).isBlank();
        if (!blankLine) {
            records.add(current);
        }
    }
}
