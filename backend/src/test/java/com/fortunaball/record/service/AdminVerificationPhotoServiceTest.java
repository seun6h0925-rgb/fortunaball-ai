package com.fortunaball.record.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.file.FileUpload;
import com.fortunaball.common.file.FileUploadRepository;
import com.fortunaball.common.file.PrivateFileStorage;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB·파일 없이 가짜로 사진 보기 흐름을 확인한다. */
class AdminVerificationPhotoServiceTest {

    private static final byte[] BYTES = {9, 8, 7};

    private final AnonymousVerificationRepository proofRepo = mock(AnonymousVerificationRepository.class);
    private final FileUploadRepository fileRepo = mock(FileUploadRepository.class);
    private final PrivateFileStorage storage = mock(PrivateFileStorage.class);
    private AdminVerificationPhotoService service;

    @BeforeEach
    void setUp() {
        service = new AdminVerificationPhotoService(proofRepo, fileRepo, storage);
    }

    private void stub(String contentType) {
        AnonymousVerification v = AnonymousVerification.register(10L, 55L, "ABCDEFGHJKLM", "a".repeat(64), null);
        FileUpload f = FileUpload.uploaded("원본.jpg", "abc.jpg", "/x/abc.jpg", 3, "jpg", contentType);
        when(proofRepo.findById(1L)).thenReturn(Optional.of(v));
        when(fileRepo.findById(55L)).thenReturn(Optional.of(f));
        when(storage.read(f)).thenReturn(BYTES);
    }

    @Test
    void JPG는_jpeg로_내려준다() {
        stub("image/jpeg");

        AdminVerificationPhotoService.Photo p = service.load(1L);

        assertArrayEquals(BYTES, p.bytes());
        assertEquals("image/jpeg", p.contentType());
        assertEquals("jpg", p.extension());
    }

    @Test
    void PNG는_png로_내려준다() {
        stub("image/png");

        AdminVerificationPhotoService.Photo p = service.load(1L);

        assertEquals("image/png", p.contentType());
        assertEquals("png", p.extension());
    }

    @Test
    void 형식_값이_이상하면_일반_파일로_내려준다() {
        stub("text/html");

        AdminVerificationPhotoService.Photo p = service.load(1L);

        assertEquals("application/octet-stream", p.contentType());
        assertEquals("bin", p.extension());
    }

    @Test
    void 없는_인증이면_NOT_FOUND() {
        when(proofRepo.findById(9L)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.load(9L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verifyNoInteractions(storage);
    }

    @Test
    void 사진_기록이_없으면_NOT_FOUND() {
        AnonymousVerification v = AnonymousVerification.register(10L, 55L, "ABCDEFGHJKLM", "a".repeat(64), null);
        when(proofRepo.findById(1L)).thenReturn(Optional.of(v));
        when(fileRepo.findById(55L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.load(1L)).getErrorCode());
        verifyNoInteractions(storage);
    }

    @Test
    void 파일이_지워졌으면_저장소가_던진_NOT_FOUND를_그대로_올린다() {
        AnonymousVerification v = AnonymousVerification.register(10L, 55L, "ABCDEFGHJKLM", "a".repeat(64), null);
        FileUpload f = FileUpload.uploaded("a.jpg", "abc.jpg", "/x/abc.jpg", 3, "jpg", "image/jpeg");
        when(proofRepo.findById(1L)).thenReturn(Optional.of(v));
        when(fileRepo.findById(55L)).thenReturn(Optional.of(f));
        when(storage.read(any(FileUpload.class))).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "사진을 찾을 수 없어요.", "x"));

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.load(1L)).getErrorCode());
    }
}
