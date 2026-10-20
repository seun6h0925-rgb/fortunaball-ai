package com.fortunaball.analyst.service;

import com.fortunaball.analyst.domain.ParsedQuestion;
import com.fortunaball.analyst.domain.QuestionType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 애널리스트 질문에서 규칙(정규식)으로 값을 뽑는다. AI 를 쓰지 않아서 비용이 들지 않고 결과가 늘 같다.
 *
 * 읽는 순서 (앞에서 읽은 부분은 "사용됨"으로 표시해서 뒤에서 숫자로 다시 읽지 않는다)
 *  1. 발급코드  12자 (대문자·숫자, 헷갈리는 I·L·O·0·1 제외. IssueCodeGenerator 와 같은 규칙)
 *  2. 기간      "최근 20회"
 *  3. 회차      "1245회차", "1245회"(3~4자리)
 *  4. 게임 수   "3게임", "5세트", "2줄", "4장", "3조합"
 *  5. 번호      1~45. 바로 뒤에 등·월·일·시·분·원·명·개·번째·% 가 붙은 숫자는 번호가 아니다 ("1등", "3시")
 *              단, "44 분석"의 분·"7 명당"의 명처럼 다른 낱말의 첫 글자인 경우는 번호로 읽는다.
 *  6. 포함/제외 번호는 "숫자 뒤에서 처음 나오는 표현"으로 가른다.
 *       "3 7 포함하고 20 빼고" → 포함 3,7 / 제외 20
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 제외 표현("넣지 마", "들어가지 않게")에 포함 표현("넣", "들어가")이 들어 있어서, 제외 표현 위치를 먼저 찾고
 *    그 자리와 겹치는 포함 표현은 버린다.
 *  - 포함·제외 번호가 같은 번호로 충돌하는지, 개수가 너무 많은지는 여기서 판단하지 않는다. (번호 생성 단계에서 검사)
 */
@Component
public class QuestionParser {

    private static final Pattern ISSUE_CODE = Pattern.compile(
            "(?<![A-Za-z0-9])[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{12}(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD = Pattern.compile("최근\\s*(\\d{1,3})\\s*회");
    private static final Pattern ROUND = Pattern.compile("(?<!\\d)(\\d{1,4})\\s*회차|(?<!\\d)(\\d{3,4})\\s*회(?!\\s*(?:간|동안))");
    private static final Pattern GAME_COUNT = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(?:게임|세트|줄|장|조합)");
    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)(?!\\s*(?:등|회|월|일(?!단|반|부|곱)|시(?!작|도)|분(?!석|명)|원(?!래)|명(?!당)|개|번째|%|게임|세트|줄|장|조합))");
    private static final Pattern EXCLUDE_WORD = Pattern.compile("제외|빼|말고|없이|안\\s*들어|들어가지\\s*않|넣지\\s*마");
    private static final Pattern INCLUDE_WORD = Pattern.compile("포함|넣|들어가|꼭|반드시");
    private static final Pattern GENERATE_WORD = Pattern.compile("만들|생성|뽑|골라|짜\\s*줘|추천");
    /** 생성 표현만으로는 "점심 메뉴 추천해줘"도 걸리므로, 로또 관련 낱말이 함께 있어야 번호 생성으로 본다 */
    private static final Pattern LOTTO_NOUN = Pattern.compile("번호|조합|게임|로또|넘버");

    public ParsedQuestion parse(String question, boolean mapContext) {
        String text = question == null ? "" : question;
        boolean[] used = new boolean[text.length()];

        String issueCode = null;
        Matcher m = ISSUE_CODE.matcher(text);
        if (m.find()) {
            issueCode = m.group().toUpperCase(Locale.ROOT);
            mark(used, m.start(), m.end());
        }

        Integer period = null;
        m = PERIOD.matcher(text);
        if (m.find()) {
            period = Integer.valueOf(m.group(1));
            mark(used, m.start(), m.end());
        }

        Integer roundNo = null;
        m = ROUND.matcher(text);
        while (m.find()) {
            if (isFree(used, m.start(), m.end())) {
                roundNo = Integer.valueOf(m.group(1) != null ? m.group(1) : m.group(2));
                mark(used, m.start(), m.end());
                break;
            }
        }

        Integer gameCount = null;
        m = GAME_COUNT.matcher(text);
        while (m.find()) {
            if (isFree(used, m.start(), m.end())) {
                gameCount = Integer.valueOf(m.group(1));
                mark(used, m.start(), m.end());
                break;
            }
        }

        // 번호 후보 (위치와 함께)
        List<int[]> numbers = new ArrayList<>();   // {값, 시작, 끝}
        m = NUMBER.matcher(text);
        while (m.find()) {
            int value = Integer.parseInt(m.group(1));
            if (value >= 1 && value <= 45 && isFree(used, m.start(), m.end())) {
                numbers.add(new int[]{value, m.start(), m.end()});
            }
        }

        // 제외 표현을 먼저 찾고, 겹치는 포함 표현은 버린다
        List<int[]> keywords = new ArrayList<>();   // {시작, 끝, 1=포함 / 2=제외}
        m = EXCLUDE_WORD.matcher(text);
        while (m.find()) {
            keywords.add(new int[]{m.start(), m.end(), 2});
        }
        List<int[]> excludeSpans = new ArrayList<>(keywords);
        m = INCLUDE_WORD.matcher(text);
        while (m.find()) {
            boolean overlap = false;
            for (int[] e : excludeSpans) {
                if (m.start() < e[1] && e[0] < m.end()) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                keywords.add(new int[]{m.start(), m.end(), 1});
            }
        }
        keywords.sort((a, b) -> Integer.compare(a[0], b[0]));

        Set<Integer> include = new LinkedHashSet<>();
        Set<Integer> exclude = new LinkedHashSet<>();
        List<Integer> unassigned = new ArrayList<>();
        for (int[] n : numbers) {
            int[] next = null;
            for (int[] k : keywords) {
                if (k[0] >= n[2]) {
                    next = k;
                    break;
                }
            }
            if (next == null) {
                unassigned.add(n[0]);
            } else if (next[2] == 1) {
                include.add(n[0]);
            } else {
                exclude.add(n[0]);
            }
        }

        // 포함/제외 표현이 없고 서로 다른 번호가 정확히 6개면 "직접 적은 번호"로 본다
        List<Integer> given = List.of();
        if (include.isEmpty() && exclude.isEmpty() && new LinkedHashSet<>(unassigned).size() == 6) {
            given = List.copyOf(new LinkedHashSet<>(unassigned));
        }

        boolean explicitGenerate = GENERATE_WORD.matcher(text).find() && LOTTO_NOUN.matcher(text).find();
        boolean generation = explicitGenerate || gameCount != null || !include.isEmpty() || !exclude.isEmpty();

        QuestionType type;
        if (mapContext) {
            type = QuestionType.MAP_STORE;
        } else if (issueCode != null) {
            type = QuestionType.ISSUE_ANALYSIS;
        } else if (generation) {
            type = QuestionType.NUMBER_GENERATION;
            given = List.of();   // 만들어 달라는 질문이면 적힌 숫자는 분석 대상이 아니다
        } else if (!given.isEmpty()) {
            type = QuestionType.NUMBER_ANALYSIS;
        } else if (roundNo != null) {
            type = QuestionType.ROUND_ANALYSIS;
        } else {
            type = QuestionType.STAT_ANALYSIS;
        }
        return new ParsedQuestion(type, gameCount, List.copyOf(include), List.copyOf(exclude), issueCode, roundNo,
                period, given);
    }

    private static void mark(boolean[] used, int start, int end) {
        for (int i = start; i < end; i++) {
            used[i] = true;
        }
    }

    private static boolean isFree(boolean[] used, int start, int end) {
        for (int i = start; i < end; i++) {
            if (used[i]) {
                return false;
            }
        }
        return true;
    }
}
