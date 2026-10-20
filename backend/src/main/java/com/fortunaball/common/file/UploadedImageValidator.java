package com.fortunaball.common.file;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 올라온 사진이 받아도 되는 파일인지 검사한다. (비어 있지 않은지 / 용량 / JPG·PNG 인지)
 *
 * ⚠ 파일 이름의 확장자나 브라우저가 알려 준 형식(Content-Type)은 믿지 않는다. 누구나 거짓으로 보낼 수 있어서다.
 *    파일 맨 앞 몇 바이트(매직 넘버)로 실제 형식을 확인하고, 저장할 확장자도 거기서 정한다.
 *    (예: exe 파일을 photo.jpg 로 바꿔 올려도 여기서 걸린다)
 */
@Component
@EnableConfigurationProperties(UploadProperties.class)
public class UploadedImageValidator {

    /** 검사를 통과한 사진의 실제 형식. */
    public record DetectedImage(String extension, String contentType) {
    }

    private static final DetectedImage JPEG = new DetectedImage("jpg", "image/jpeg");
    private static final DetectedImage PNG = new DetectedImage("png", "image/png");

    private final UploadProperties props;

    public UploadedImageValidator(UploadProperties props) {
        this.props = props;
    }

    public DetectedImage validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진을 선택해 주세요.", "빈 파일");
        }
        if (bytes.length > props.maxBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "사진 용량이 너무 커요. " + props.maxSizeMb() + "MB 이하로 올려 주세요.",
                    "파일 크기 초과: " + bytes.length + " byte");
        }
        if (isJpeg(bytes)) {
            return JPEG;
        }
        if (isPng(bytes)) {
            return PNG;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "JPG 또는 PNG 사진만 올릴 수 있어요.",
                "허용되지 않는 파일 형식 (맨 앞 바이트가 JPG/PNG 와 다름)");
    }

    /** JPG: FF D8 FF 로 시작 */
    private static boolean isJpeg(byte[] b) {
        return b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    /** PNG: 89 50 4E 47 0D 0A 1A 0A 로 시작 */
    private static boolean isPng(byte[] b) {
        int[] magic = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((b[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
