package com.fortunaball.ai.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class CachedAiPolicySourceTest {

    private static final List<AiPolicy> ONE = List.of(new AiPolicy("A", "A", "ALL", 1, "m", List.of("k")));

    @Test
    void 짧은_시간_안에는_DB를_다시_읽지_않는다() {
        AiPolicyRepository repository = mock(AiPolicyRepository.class);
        when(repository.loadActive()).thenReturn(ONE);
        CachedAiPolicySource source = new CachedAiPolicySource(repository);

        assertEquals(ONE, source.activePolicies());
        assertEquals(ONE, source.activePolicies());
        verify(repository, times(1)).loadActive();
    }

    @Test
    void DB_읽기에_실패해도_서비스는_멈추지_않고_빈_목록을_쓴다() {
        AiPolicyRepository repository = mock(AiPolicyRepository.class);
        when(repository.loadActive()).thenThrow(new RuntimeException("db down"));
        CachedAiPolicySource source = new CachedAiPolicySource(repository);

        assertEquals(List.of(), source.activePolicies());
    }
}
