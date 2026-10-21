package com.fortunaball.admin.store.controller;

import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.store.dto.AdminStoreItem;
import com.fortunaball.admin.store.dto.StoreUpdateRequest;
import com.fortunaball.admin.store.dto.StoreUploadApplyResult;
import com.fortunaball.admin.store.dto.StoreUploadDetail;
import com.fortunaball.admin.store.dto.StoreUploadResult;
import com.fortunaball.admin.store.dto.StoreUploadRow;
import com.fortunaball.admin.store.service.AdminStoreService;
import com.fortunaball.admin.store.service.AdminStoreUploadService;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.response.ApiResponse;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 판매점 관리 (관리자). API-30 파일 업로드, 49 변경점 비교, 50 반영, 51 취소, 52 업로드 이력, 53 목록, 31 수정.
 * 로그인(관리자 세션)과 CSRF 헤더(X-XSRF-TOKEN)가 필요하다. (GET 은 CSRF 헤더 불필요)
 */
@RestController
@RequestMapping("/api/admin/stores")
public class AdminStoreController {

    private final AdminStoreUploadService uploadService;
    private final AdminStoreService storeService;

    public AdminStoreController(AdminStoreUploadService uploadService, AdminStoreService storeService) {
        this.uploadService = uploadService;
        this.storeService = storeService;
    }

    /** multipart/form-data: file(파일), uploadType(파일 종류, 현재 FIRST_SECOND_CSV) */
    @PostMapping("/uploads")
    public ApiResponse<StoreUploadResult> upload(@RequestParam("file") MultipartFile file,
                                                 @RequestParam("uploadType") String uploadType) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일을 읽지 못했어요. 다시 올려 주세요.",
                    "판매점 업로드 파일 읽기 실패: " + e);
        }
        return ApiResponse.ok(uploadService.upload(file.getOriginalFilename(), bytes, uploadType),
                "파일을 확인했어요. 변경점을 확인한 뒤 반영해 주세요.");
    }

    @GetMapping("/uploads")
    public ApiResponse<AdminPage<StoreUploadRow>> history(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(uploadService.history(from, to, status, page, size));
    }

    @GetMapping("/uploads/{uploadId}")
    public ApiResponse<StoreUploadDetail> detail(@PathVariable("uploadId") long uploadId,
                                                 @RequestParam(value = "kind", required = false) String kind,
                                                 @RequestParam(value = "page", required = false) Integer page,
                                                 @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(uploadService.detail(uploadId, kind, page, size));
    }

    @PostMapping("/uploads/{uploadId}/apply")
    public ApiResponse<StoreUploadApplyResult> apply(@PathVariable("uploadId") long uploadId) {
        return ApiResponse.ok(uploadService.apply(uploadId), "판매점 데이터를 반영했어요.");
    }

    @PostMapping("/uploads/{uploadId}/cancel")
    public ApiResponse<StoreUploadRow> cancel(@PathVariable("uploadId") long uploadId) {
        return ApiResponse.ok(uploadService.cancel(uploadId), "업로드를 취소했어요.");
    }

    @GetMapping
    public ApiResponse<AdminPage<AdminStoreItem>> list(
            @RequestParam(value = "sido", required = false) String sido,
            @RequestParam(value = "sigungu", required = false) String sigungu,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "geocodeStatus", required = false) String geocodeStatus,
            @RequestParam(value = "closed", required = false) Boolean closed,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(storeService.list(sido, sigungu, keyword, geocodeStatus, closed, page, size));
    }

    @PatchMapping("/{storeId}")
    public ApiResponse<AdminStoreItem> update(@PathVariable("storeId") long storeId,
                                              @RequestBody StoreUpdateRequest request) {
        return ApiResponse.ok(storeService.update(storeId, request), "판매점 정보를 수정했어요.");
    }
}
