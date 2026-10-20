package com.fortunaball.record.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.file.FileUpload;
import com.fortunaball.common.file.PrivateFileStorage;
import com.fortunaball.common.file.UploadedImageValidator;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.issue.domain.IssueNumber;
import com.fortunaball.issue.domain.IssueType;
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import com.fortunaball.record.dto.ProofRegisterResponse;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import com.fortunaball.ticket.domain.TicketQrImageReader;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DB·파일 없이 가짜(Mockito)로 인증 등록 흐름을 확인한다.
 * ⚠ 일련번호는 가짜 값이다. (저장소가 퍼블릭)
 * 📚 TransactionTemplate 은 가짜로 만들고, 받은 코드를 그대로 실행하도록 해서 "트랜잭션 안" 로직을 확인한다.
 */
class ProofRegisterServiceTest {

    private static final String CODE = "ABCDEFGHJKLM";
    private static final int ROUND = 1239;
    /** 1239회, 1게임(수동 1~6), 가짜 일련번호 */
    private static final String QR = "http://qr.dhlottery.co.kr/?v=1239m010203040506100000000112345678";
    private static final byte[] PHOTO = {1, 2, 3};

    private final RequestRateLimiter limiter = mock(RequestRateLimiter.class);
    private final RateLimitProperties props = new RateLimitProperties(30, 30, 30, 5);
    private final UploadedImageValidator validator = mock(UploadedImageValidator.class);
    private final TicketQrImageReader reader = mock(TicketQrImageReader.class);
    private final NumberIssueRepository issueRepo = mock(NumberIssueRepository.class);
    private final IssueNumberRepository numberRepo = mock(IssueNumberRepository.class);
    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final WinningVerificationRepository winningRepo = mock(WinningVerificationRepository.class);
    private final AnonymousVerificationRepository proofRepo = mock(AnonymousVerificationRepository.class);
    private final PrivateFileStorage storage = mock(PrivateFileStorage.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private ProofRegisterService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        service = new ProofRegisterService(limiter, props, validator, reader, issueRepo, numberRepo, drawRepo,
                winningRepo, proofRepo, storage, tx);
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(QR));
    }

    private static BusinessException reject(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    private ProofRegisterResponse register() {
        return service.register(CODE, true, "대박", "t.jpg", PHOTO, "1.1.1.1");
    }

    /** 발급 건(id 10, 대상 회차 1239) + 게임 1 = 1~6 (발급 번호 id 101) */
    private void stubIssue(int targetRound) {
        NumberIssue issue = NumberIssue.create(CODE, "guest-aaaaaaaaaaaaaaaa", targetRound, 1, IssueType.STAT_AUTO, null, null);
        ReflectionTestUtils.setField(issue, "id", 10L);
        IssueNumber g = IssueNumber.of(10L, 1, "01-02-03-04-05-06", List.of(1, 2, 3, 4, 5, 6));
        ReflectionTestUtils.setField(g, "id", 101L);
        when(issueRepo.findByIssueCode(CODE)).thenReturn(Optional.of(issue));
        when(numberRepo.findByNumberIssueIdOrderByGameOrderAsc(10L)).thenReturn(List.of(g));
    }

    private void stubDraw(List<Integer> winning, int bonus) {
        when(drawRepo.findByRoundNo(ROUND)).thenReturn(Optional.of(
                new DrawRound(ROUND, LocalDate.of(2026, 8, 29), winning, bonus, "TEST")));
    }

    private void stubSave() {
        FileUpload f = FileUpload.uploaded("t.jpg", "abc.jpg", "/x/abc.jpg", 3, "jpg", "image/jpeg");
        ReflectionTestUtils.setField(f, "id", 55L);
        when(storage.save(anyString(), any(byte[].class))).thenReturn(f);
        when(proofRepo.saveAndFlush(any(AnonymousVerification.class))).thenAnswer(inv -> {
            AnonymousVerification v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 900L);
            return v;
        });
    }

    @Test
    void 정상이면_검수대기로_저장되고_검증결과와_파일이_저장된다() {
        stubIssue(ROUND);
        stubDraw(List.of(1, 2, 3, 4, 5, 6), 7);   // 1등
        stubSave();

        ProofRegisterResponse res = register();

        assertEquals(900L, res.verificationId());
        assertEquals("PENDING", res.reviewStatus());
        verify(winningRepo).insertIfAbsent(eq(101L), eq(ROUND), eq(6), eq(false), eq(1), eq("VERIFIED"));
        verify(storage).save(eq("t.jpg"), any(byte[].class));
        verify(proofRepo).saveAndFlush(any(AnonymousVerification.class));
        verify(limiter).check("proof-register", "1.1.1.1", 5);
    }

    @Test
    void 조회횟수_제한에_걸리면_아무것도_하지_않는다() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "많아요", "x"))
                .when(limiter).check(anyString(), anyString(), anyInt());

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, reject(this::register).getErrorCode());
        verifyNoInteractions(validator, reader, issueRepo, storage);
    }

    @Test
    void 동의하지_않으면_거절한다() {
        BusinessException e = reject(() -> service.register(CODE, false, null, "t.jpg", PHOTO, "1.1.1.1"));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        verifyNoInteractions(validator, reader, storage);
    }

    @Test
    void 발급코드_형식이_틀리면_없는_코드로_안내한다() {
        BusinessException e = reject(() -> service.register("abc", true, null, "t.jpg", PHOTO, "1.1.1.1"));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verifyNoInteractions(reader, storage);
    }

    @Test
    void 소문자_공백_발급코드는_정리해서_조회한다() {
        stubIssue(ROUND);
        stubDraw(List.of(1, 2, 3, 4, 5, 6), 7);
        stubSave();
        service.register("  abcdefghjklm ", true, null, "t.jpg", PHOTO, "1.1.1.1");
        verify(issueRepo).findByIssueCode(CODE);
    }

    @Test
    void 한마디가_300자를_넘으면_거절한다() {
        BusinessException e = reject(() -> service.register(CODE, true, "가".repeat(301), "t.jpg", PHOTO, "1.1.1.1"));
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 사진_형식이_틀리면_QR_읽기까지_가지_않는다() {
        when(validator.validate(any(byte[].class)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "JPG/PNG만 가능", "x"));

        assertEquals(ErrorCode.INVALID_INPUT, reject(this::register).getErrorCode());
        verifyNoInteractions(reader, storage);
    }

    @Test
    void QR을_못_읽으면_거절하고_저장하지_않는다() {
        when(reader.read(any(byte[].class))).thenReturn(Optional.empty());

        BusinessException e = reject(this::register);
        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        assertEquals(ProofRegisterService.MSG_NO_QR, e.getMessage());
        verifyNoInteractions(issueRepo, storage);
    }

    @Test
    void 로또_용지_QR이_아니면_거절한다() {
        when(reader.read(any(byte[].class))).thenReturn(Optional.of("https://example.com/hello"));

        assertEquals(ProofRegisterService.MSG_BAD_QR, reject(this::register).getMessage());
        verifyNoInteractions(issueRepo, storage);
    }

    @Test
    void 일련번호가_없는_QR은_거절한다() {
        when(reader.read(any(byte[].class))).thenReturn(Optional.of("http://qr.dhlottery.co.kr/?v=1239m010203040506"));

        assertEquals(ErrorCode.INVALID_INPUT, reject(this::register).getErrorCode());
        verifyNoInteractions(issueRepo, storage);
    }

    @Test
    void 없는_발급코드면_NOT_FOUND() {
        when(issueRepo.findByIssueCode(CODE)).thenReturn(Optional.empty());

        BusinessException e = reject(this::register);
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(storage, never()).save(anyString(), any(byte[].class));
    }

    @Test
    void 용지_회차와_발급_대상_회차가_다르면_거절한다() {
        stubIssue(1240);

        assertEquals(ErrorCode.INVALID_INPUT, reject(this::register).getErrorCode());
        verify(storage, never()).save(anyString(), any(byte[].class));
    }

    @Test
    void 아직_추첨_전이면_CONFLICT() {
        stubIssue(ROUND);
        when(drawRepo.findByRoundNo(ROUND)).thenReturn(Optional.empty());

        assertEquals(ProofRegisterService.MSG_NOT_DRAWN, reject(this::register).getMessage());
    }

    @Test
    void 용지에_발급번호가_없으면_거절한다() {
        stubIssue(ROUND);
        stubDraw(List.of(1, 2, 3, 4, 5, 6), 7);
        when(reader.read(any(byte[].class))).thenReturn(
                Optional.of("http://qr.dhlottery.co.kr/?v=1239m070809101112100000000112345678"));

        assertEquals(ProofRegisterService.MSG_NO_MATCH, reject(this::register).getMessage());
        verify(storage, never()).save(anyString(), any(byte[].class));
    }

    @Test
    void 낙첨이면_인증할_수_없다() {
        stubIssue(ROUND);
        stubDraw(List.of(10, 20, 30, 40, 41, 42), 43);   // 일치 0개

        assertEquals(ProofRegisterService.MSG_NOT_WINNER, reject(this::register).getMessage());
        verifyNoInteractions(winningRepo);
        verify(storage, never()).save(anyString(), any(byte[].class));
    }

    @Test
    void 이미_인증된_발급코드면_거절한다() {
        stubIssue(ROUND);
        stubDraw(List.of(1, 2, 3, 4, 5, 6), 7);
        when(proofRepo.existsByNumberIssueIdAndReviewStatusNot(10L, ReviewStatus.REJECTED)).thenReturn(true);

        assertEquals(ProofRegisterService.MSG_DUP_ISSUE, reject(this::register).getMessage());
        verify(storage, never()).save(anyString(), any(byte[].class));
    }

    @Test
    void 이미_등록된_용지면_거절한다() {
        stubIssue(ROUND);
        stubDraw(List.of(1, 2, 3, 4, 5, 6), 7);
        when(proofRepo.existsByTicketHashAndReviewStatusNot(anyString(), eq(ReviewStatus.REJECTED))).thenReturn(true);

        assertEquals(ProofRegisterService.MSG_DUP_TICKET, reject(this::register).getMessage());
        verify(storage, never()).save(anyString(), any(byte[].class));
    }

    @Test
    void 동시_등록으로_DB_UNIQUE에_걸리면_안내_문구로_바꾼다() {
        stubIssue(ROUND);
        stubDraw(List.of(1, 2, 3, 4, 5, 6), 7);
        stubSave();
        when(proofRepo.saveAndFlush(any(AnonymousVerification.class))).thenThrow(new DataIntegrityViolationException("dup"));
        // 저장 시도 전에는 중복이 없었고, 예외 뒤 다시 확인하니 발급코드가 이미 쓰였다
        when(proofRepo.existsByNumberIssueIdAndReviewStatusNot(10L, ReviewStatus.REJECTED)).thenReturn(false, true);

        assertEquals(ProofRegisterService.MSG_DUP_ISSUE, reject(this::register).getMessage());
    }

    @Test
    void UNIQUE_위반_원인을_못_찾으면_원래_예외를_그대로_올린다() {
        stubIssue(ROUND);
        stubDraw(List.of(1, 2, 3, 4, 5, 6), 7);
        stubSave();
        DataIntegrityViolationException original = new DataIntegrityViolationException("other");
        when(proofRepo.saveAndFlush(any(AnonymousVerification.class))).thenThrow(original);

        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, this::register);
        assertSame(original, e);
    }
}
