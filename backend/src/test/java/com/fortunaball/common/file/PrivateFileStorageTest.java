package com.fortunaball.common.file;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 비공개 파일 보관소 테스트. 임시 폴더(@TempDir)에 실제로 파일을 쓰고, DB 는 가짜(Mockito)로 대신한다.
 * 확인하는 것: 임의 이름 저장 / 이름 정리 / 설정 오류 / DB 실패 시 파일 삭제 / 읽기·삭제 / 이상한 이름 거부
 */
class PrivateFileStorageTest {

    @TempDir
    Path tempDir;

    private FileUploadRepository repository;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    @BeforeEach
    void setUp() {
        repository = mock(FileUploadRepository.class);
        when(repository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PrivateFileStorage storage(String dir) {
        UploadProperties props = new UploadProperties(dir, 10);
        return new PrivateFileStorage(props, new UploadedImageValidator(props), repository);
    }

    private long fileCount(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void 임의_이름으로_저장하고_기록을_돌려준다() throws IOException {
        Path dir = tempDir.resolve("uploads");   // 아직 없는 폴더 → 자동으로 만든다
        FileUpload saved = storage(dir.toString()).save("내 복권.jpg", JPEG);

        assertTrue(saved.getStoredFileName().matches("^[0-9a-f]{32}\\.jpg$"));
        assertEquals("내 복권.jpg", saved.getOriginalFileName());
        assertEquals("image/jpeg", saved.getContentType());
        assertEquals("jpg", saved.getFileExtension());
        assertEquals(JPEG.length, saved.getFileSize());
        assertEquals(FileUploadStatus.UPLOADED, saved.getUploadStatus());
        assertArrayEquals(JPEG, Files.readAllBytes(dir.resolve(saved.getStoredFileName())));
    }

    @Test
    void 같은_사진을_두_번_저장해도_이름이_다르다() {
        PrivateFileStorage storage = storage(tempDir.toString());
        FileUpload a = storage.save("a.jpg", JPEG);
        FileUpload b = storage.save("a.jpg", JPEG);
        assertFalse(a.getStoredFileName().equals(b.getStoredFileName()));
    }

    @Test
    void 올린_이름의_경로와_제어문자는_지운다() {
        assertEquals("secret.jpg", PrivateFileStorage.sanitizeName("../../etc/secret.jpg"));
        assertEquals("secret.jpg", PrivateFileStorage.sanitizeName("C:\\Users\\me\\secret.jpg"));
        assertEquals("ab.jpg", PrivateFileStorage.sanitizeName("a\u0000b.jpg"));
        assertEquals("photo", PrivateFileStorage.sanitizeName("   "));
        assertEquals("photo", PrivateFileStorage.sanitizeName(null));
        assertEquals(255, PrivateFileStorage.sanitizeName("x".repeat(400)).length());
    }

    @Test
    void 사진이_아니면_저장하지_않는다() throws IOException {
        PrivateFileStorage storage = storage(tempDir.toString());
        assertThrows(BusinessException.class, () -> storage.save("a.jpg", "글자".getBytes()));
        assertEquals(0, fileCount(tempDir));
    }

    @Test
    void 업로드_폴더가_비어_있으면_서비스_이용불가_오류() {
        BusinessException e = assertThrows(BusinessException.class, () -> storage("").save("a.jpg", JPEG));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void 상대_경로_폴더는_거부() {
        BusinessException e = assertThrows(BusinessException.class, () -> storage("uploads").save("a.jpg", JPEG));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void 경로에_제어문자가_섞여_있으면_원인을_알려_주는_오류() {
        // .env 에서 C:\fortuna 의 \f 가 줄바꿈 문자로 읽힌 상황을 흉내 낸다.
        BusinessException e = assertThrows(BusinessException.class,
                () -> storage("C:mywssemi_project\u000Cortuna-data-uploads").save("a.jpg", JPEG));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
        assertTrue(e.getDetail().contains("슬래시"));
    }

    @Test
    void DB_저장이_실패하면_방금_쓴_파일을_지운다() throws IOException {
        when(repository.save(any(FileUpload.class))).thenThrow(new IllegalStateException("DB 오류"));

        assertThrows(IllegalStateException.class, () -> storage(tempDir.toString()).save("a.jpg", JPEG));

        assertEquals(0, fileCount(tempDir));
    }

    @Test
    void 저장한_사진을_읽을_수_있다() {
        PrivateFileStorage storage = storage(tempDir.toString());
        FileUpload saved = storage.save("a.jpg", JPEG);

        assertArrayEquals(JPEG, storage.read(saved));
    }

    @Test
    void 삭제하면_파일이_사라지고_상태가_DELETED_가_된다() throws IOException {
        PrivateFileStorage storage = storage(tempDir.toString());
        FileUpload saved = storage.save("a.jpg", JPEG);

        storage.delete(saved);

        assertEquals(FileUploadStatus.DELETED, saved.getUploadStatus());
        assertEquals(0, fileCount(tempDir));
        // 지운 사진은 읽을 수 없다.
        BusinessException e = assertThrows(BusinessException.class, () -> storage.read(saved));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void 우리가_만든_이름_모양이_아니면_읽기를_거부() {
        PrivateFileStorage storage = storage(tempDir.toString());
        FileUpload forged = FileUpload.uploaded("a.jpg", "../../etc/passwd", "x", 1, "jpg", "image/jpeg");

        BusinessException e = assertThrows(BusinessException.class, () -> storage.read(forged));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
