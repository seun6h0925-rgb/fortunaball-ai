package com.fortunaball.record.controller;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.record.domain.ReviewStatus;
import com.fortunaball.record.dto.AdminVerificationListResponse;
import com.fortunaball.record.dto.VerificationReviewRequest;
import com.fortunaball.record.dto.VerificationReviewResponse;
import com.fortunaball.record.service.AdminVerificationPhotoService;
import com.fortunaball.record.service.AdminVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 인증 검수 (API-28 목록, API-29 검수 처리, API-62 사진 보기).
 *
 * 로그인한 관리자만 쓸 수 있다. (/api/admin/** 전체를 SecurityConfig 가 막는다. 로그인 없으면 401, 데이터를 바꾸는 요청은 CSRF 헤더 필요)
 */
@RestController
@RequestMapping("/api/admin/verifications")
public class AdminVerificationController {

    private final AdminVerificationService service;
    private final AdminVerificationPhotoService photoService;

    public AdminVerificationController(AdminVerificationService service, AdminVerificationPhotoService photoService) {
        this.service = service;
        this.photoService = photoService;
    }

    /** status: PENDING/APPROVED/REJECTED/HIDDEN (비우면 전체) */
    @GetMapping
    public ApiResponse<AdminVerificationListResponse> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(parseStatus(status), page, size));
    }

    @PatchMapping("/{verificationId}")
    public ApiResponse<VerificationReviewResponse> review(@PathVariable("verificationId") Long verificationId,
                                                          @Valid @RequestBody VerificationReviewRequest request) {
        return ApiResponse.ok(service.review(verificationId, request));
    }

    /**
     * 인증 사진 보기 (API-62). 사진 내용을 이미지로 그대로 돌려준다.
     * ⚠ 개인정보가 담길 수 있는 사진이라 브라우저·중간 서버가 저장해 두지 못하게(no-store) 하고,
     *    형식을 추측해서 실행하지 못하게(nosniff) 한다. 원래 파일 이름은 내려주지 않는다.
     */
    @GetMapping("/{verificationId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable("verificationId") Long verificationId) {
        AdminVerificationPhotoService.Photo photo = photoService.load(verificationId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"proof-" + verificationId + "." + photo.extension() + "\"")
                .body(photo.bytes());
    }

    private static ReviewStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReviewStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상태 값을 확인해 주세요.", "status 값 오류");
        }
    }
}
