package com.fortunaball.common.file;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 사진 검사 규칙(JPG/PNG 만, 용량 제한, 이름이 아니라 내용으로 판단)을 확인한다. */
class UploadedImageValidatorTest {

    private final UploadedImageValidator validator =
            new UploadedImageValidator(new UploadProperties("C:/unused", 1)); // 한도 1MB 로 시험

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    }

    @Test
    void JPG_는_통과하고_확장자가_jpg() {
        UploadedImageValidator.DetectedImage image = validator.validate(jpeg());
        assertEquals("jpg", image.extension());
        assertEquals("image/jpeg", image.contentType());
    }

    @Test
    void PNG_는_통과하고_확장자가_png() {
        UploadedImageValidator.DetectedImage image = validator.validate(png());
        assertEquals("png", image.extension());
        assertEquals("image/png", image.contentType());
    }

    @Test
    void 이름이_아니라_내용으로_판단한다_글자_파일은_거절() {
        byte[] text = "이건 사진이 아니에요".getBytes(StandardCharsets.UTF_8);
        BusinessException e = assertThrows(BusinessException.class, () -> validator.validate(text));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void GIF_와_PDF_도_거절() {
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        byte[] pdf = "%PDF-1.7....".getBytes(StandardCharsets.US_ASCII);
        assertThrows(BusinessException.class, () -> validator.validate(gif));
        assertThrows(BusinessException.class, () -> validator.validate(pdf));
    }

    @Test
    void 짧은_파일도_오류없이_거절() {
        BusinessException e = assertThrows(BusinessException.class, () -> validator.validate(new byte[]{(byte) 0xFF}));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 빈_파일과_null_은_거절() {
        assertThrows(BusinessException.class, () -> validator.validate(new byte[0]));
        assertThrows(BusinessException.class, () -> validator.validate(null));
    }

    @Test
    void 용량_한도를_넘으면_413_코드로_거절() {
        byte[] big = new byte[1024 * 1024 + 1];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;
        BusinessException e = assertThrows(BusinessException.class, () -> validator.validate(big));
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, e.getErrorCode());
    }

    @Test
    void 정확히_한도만큼은_통과() {
        byte[] exact = new byte[1024 * 1024];
        exact[0] = (byte) 0xFF; exact[1] = (byte) 0xD8; exact[2] = (byte) 0xFF;
        assertEquals("jpg", validator.validate(exact).extension());
    }
}
