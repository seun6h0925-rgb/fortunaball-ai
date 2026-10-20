package com.fortunaball.verify.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.draw.service.SchedulerLogRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificationBatchRunnerTest {

    private VerificationBatchService batchService;
    private SchedulerLogRecorder recorder;
    private VerificationBatchRunner runner;

    @BeforeEach
    void setUp() {
        batchService = mock(VerificationBatchService.class);
        recorder = mock(SchedulerLogRecorder.class);
        runner = new VerificationBatchRunner(batchService, recorder);
        when(recorder.start(anyString(), isNull())).thenReturn(7L);
    }

    @Test
    void 판정할_게임이_없으면_실행_기록도_남기지_않는다() {
        when(batchService.hasPending()).thenReturn(false);
        runner.run();
        verify(batchService, never()).verifyAll();
        verify(recorder, never()).start(anyString(), isNull());
    }

    @Test
    void 모두_성공하면_성공으로_기록한다() {
        when(batchService.hasPending()).thenReturn(true);
        when(batchService.verifyAll()).thenReturn(new VerificationBatchService.Result(3, 0));
        runner.run();
        verify(recorder).start(VerificationBatchRunner.JOB_NAME, null);
        verify(recorder).success(7L, 3);
    }

    @Test
    void 실패한_게임이_있으면_실패로_기록한다() {
        when(batchService.hasPending()).thenReturn(true);
        when(batchService.verifyAll()).thenReturn(new VerificationBatchService.Result(2, 1));
        runner.run();
        verify(recorder).failed(eq(7L), eq(2), contains("1건"));
    }

    @Test
    void 예기치_못한_오류도_밖으로_던지지_않고_실패로_기록한다() {
        when(batchService.hasPending()).thenReturn(true);
        when(batchService.verifyAll()).thenThrow(new IllegalStateException("boom"));
        runner.run();   // 예외가 나오면 이 테스트가 실패한다
        verify(recorder).failed(eq(7L), anyInt(), contains("boom"));
    }
}
