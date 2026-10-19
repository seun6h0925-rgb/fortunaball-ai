package com.fortunaball.stat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StatSnapshotTriggerTest {

    @Test
    void 정상이면_sync를_실행하고_true를_돌려준다() {
        StatSnapshotService service = mock(StatSnapshotService.class);
        StatSnapshotTrigger trigger = new StatSnapshotTrigger(service);

        assertTrue(trigger.refresh("시험"));
        verify(service, times(1)).sync();
    }

    @Test
    void 오류가_나도_밖으로_던지지_않고_false를_돌려준다() {
        StatSnapshotService service = mock(StatSnapshotService.class);
        doThrow(new IllegalStateException("DB 오류")).when(service).sync();
        StatSnapshotTrigger trigger = new StatSnapshotTrigger(service);

        assertFalse(trigger.refresh("시험"));
        // 오류 뒤에도 잠금이 풀려 다음 실행이 막히지 않는다
        assertFalse(trigger.refresh("시험2"));
        verify(service, times(2)).sync();
    }
}
