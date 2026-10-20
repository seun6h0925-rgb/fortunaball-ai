package com.fortunaball.record.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.record.dto.WinningRecordPublicResponse;
import com.fortunaball.verify.domain.WinningVerification;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB 없이 가짜로 당첨 기록 공개 설정(API-27)을 확인한다. */
class AdminWinningRecordServiceTest {

    private final WinningVerificationRepository winningRepo = mock(WinningVerificationRepository.class);
    private AdminWinningRecordService service;

    @BeforeEach
    void setUp() {
        service = new AdminWinningRecordService(winningRepo);
    }

    private void stub(long id, Integer rank) {
        WinningVerification w = mock(WinningVerification.class);
        when(w.getWinningRank()).thenReturn(rank);
        when(winningRepo.findById(id)).thenReturn(Optional.of(w));
    }

    @Test
    void 당첨_기록을_공개하고_비공개로_되돌린다() {
        stub(5L, 4);

        WinningRecordPublicResponse on = service.setPublic(5L, true);
        WinningRecordPublicResponse off = service.setPublic(5L, false);

        assertTrue(on.publicVisible());
        assertFalse(off.publicVisible());
        assertEquals(5L, on.recordId());
        verify(winningRepo).updatePublicById(5L, true);
        verify(winningRepo).updatePublicById(5L, false);
    }

    @Test
    void 없는_기록이면_NOT_FOUND() {
        when(winningRepo.findById(9L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.setPublic(9L, true)).getErrorCode());
        verify(winningRepo, never()).updatePublicById(anyLong(), anyBoolean());
    }

    @Test
    void 낙첨_기록은_공개할_수_없다() {
        stub(6L, null);

        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class, () -> service.setPublic(6L, true)).getErrorCode());
        verify(winningRepo, never()).updatePublicById(anyLong(), anyBoolean());
    }
}
