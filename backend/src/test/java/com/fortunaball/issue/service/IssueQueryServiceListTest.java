package com.fortunaball.issue.service;

import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.dto.IssueListResponse;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 발급 내역 목록(API-42)의 "첫 번째 게임 번호" 값만 따로 확인한다. (DB 없이 가짜 저장소 사용) */
class IssueQueryServiceListTest {

    private static final GuestToken GUEST = GuestToken.of("guest-aaaaaaaaaaaaaaaa");

    private final NumberIssueRepository issueRepo = mock(NumberIssueRepository.class);
    private final IssueNumberRepository numberRepo = mock(IssueNumberRepository.class);
    private IssueQueryService service;

    @BeforeEach
    void setUp() {
        service = new IssueQueryService(issueRepo, numberRepo, mock(RequestRateLimiter.class), mock(RateLimitProperties.class));
    }

    private NumberIssue issue(long id, String code) {
        NumberIssue n = NumberIssue.create(code, GUEST.value(), 1247, 2, IssueType.STAT_AUTO, null, null);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Test
    void 목록_항목에_첫_게임_번호가_들어간다() {
        NumberIssue a = issue(10L, "ABCDEFGHJKMN");
        NumberIssue b = issue(11L, "PQRSTUVWXYZ2");
        when(issueRepo.findByGuestTokenOrderByCreatedAtDescIdDesc(eq(GUEST.value()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2));
        // 10번 건만 첫 게임이 조회된다 (11번은 없음 → null)
        when(numberRepo.findByNumberIssueIdInAndGameOrder(any(), eq(1)))
                .thenReturn(List.of(IssueNumber.of(10L, 1, "03-14-22-35-38-44", List.of(3, 14, 22, 35, 38, 44))));

        IssueListResponse res = service.getList(GUEST, null, null);

        assertEquals(List.of(3, 14, 22, 35, 38, 44), res.items().get(0).firstGame());
        assertNull(res.items().get(1).firstGame());
    }

    @Test
    void 발급_내역이_없으면_첫_게임을_조회하지_않고_빈_목록() {
        when(issueRepo.findByGuestTokenOrderByCreatedAtDescIdDesc(eq(GUEST.value()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        IssueListResponse res = service.getList(GUEST, null, null);

        assertEquals(0, res.items().size());
    }
}
