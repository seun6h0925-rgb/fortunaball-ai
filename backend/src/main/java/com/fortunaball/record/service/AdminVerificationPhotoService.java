package com.fortunaball.record.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.file.FileUpload;
import com.fortunaball.common.file.FileUploadRepository;
import com.fortunaball.common.file.PrivateFileStorage;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import org.springframework.stereotype.Service;

/**
 * 관리자 인증 사진 보기 (API-62).
 * 📚 배운 내용: @Service
 *
 * ⚠ 파일 경로를 받지 않는다. "인증 번호 → 사진 기록(file_upload) → 비공개 폴더의 파일" 순서로만 찾는다.
 *    (그래서 주소에 ../ 같은 경로를 넣어도 다른 파일을 열 수 없다)
 * ⚠ 내려주는 형식은 JPG/PNG 로 고정한다. DB 의 형식 값이 이상하면 일반 파일로 내려 브라우저가 실행·해석하지 못하게 한다.
 */
@Service
public class AdminVerificationPhotoService {

    /** 사진 내용과 형식 */
    public record Photo(byte[] bytes, String contentType, String extension) {
    }

    private final AnonymousVerificationRepository proofRepo;
    private final FileUploadRepository fileRepo;
    private final PrivateFileStorage storage;

    public AdminVerificationPhotoService(AnonymousVerificationRepository proofRepo, FileUploadRepository fileRepo,
                                         PrivateFileStorage storage) {
        this.proofRepo = proofRepo;
        this.fileRepo = fileRepo;
        this.storage = storage;
    }

    public Photo load(Long verificationId) {
        AnonymousVerification v = proofRepo.findById(verificationId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "인증을 찾을 수 없어요.", "verificationId=" + verificationId));
        FileUpload file = fileRepo.findById(v.getFileUploadId()).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "사진을 찾을 수 없어요.",
                        "file_upload 없음: verificationId=" + verificationId));
        byte[] bytes = storage.read(file);   // 지워졌거나 파일이 없으면 NOT_FOUND
        String type = file.getContentType();
        if ("image/jpeg".equals(type)) {
            return new Photo(bytes, type, "jpg");
        }
        if ("image/png".equals(type)) {
            return new Photo(bytes, type, "png");
        }
        return new Photo(bytes, "application/octet-stream", "bin");
    }
}
