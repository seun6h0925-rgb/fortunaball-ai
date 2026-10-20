package com.fortunaball.record.controller;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.record.dto.ProofRegisterResponse;
import com.fortunaball.record.service.ProofRegisterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 익명 당첨 인증 등록 (API-16). 로그인 없이 쓴다. (회원가입 없는 서비스)
 *
 * 요청: multipart/form-data
 *   photo     사진 1장 (JPG/PNG, 최대 10MB)  ← 2장 이상이면 거절
 *   issueCode 발급코드
 *   consent   사진 저장·검수 동의 (true 여야 등록)
 *   comment   한마디 (선택, 300자까지)
 *
 * ⚠ 접속지(IP) 기준 조회 횟수 제한은 서비스에서 한다. (배포 후 프록시 뒤에서는 접속지 구하는 방식을 다시 점검해야 한다)
 */
@RestController
@RequestMapping("/api/verifications")
public class ProofController {

    private final ProofRegisterService service;

    public ProofController(ProofRegisterService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProofRegisterResponse> register(
            @RequestParam("photo") List<MultipartFile> photos,
            @RequestParam("issueCode") String issueCode,
            @RequestParam(value = "consent", defaultValue = "false") boolean consent,
            @RequestParam(value = "comment", required = false) String comment,
            HttpServletRequest http) {

        if (photos == null || photos.size() != 1 || photos.get(0).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진은 1장만 올릴 수 있어요.",
                    "사진 개수 오류: " + (photos == null ? 0 : photos.size()));
        }
        MultipartFile photo = photos.get(0);
        byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진을 읽지 못했어요. 다시 올려 주세요.",
                    "사진 읽기 실패: " + e.getClass().getSimpleName());
        }
        return ApiResponse.ok(service.register(issueCode, consent, comment,
                photo.getOriginalFilename(), bytes, http.getRemoteAddr()));
    }
}
