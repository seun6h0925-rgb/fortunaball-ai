package com.fortunaball.record.service;

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
import com.fortunaball.issue.domain.NumberIssue;
import com.fortunaball.issue.repository.IssueNumberRepository;
import com.fortunaball.issue.repository.NumberIssueRepository;
import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import com.fortunaball.record.dto.ProofRegisterResponse;
import com.fortunaball.record.repository.AnonymousVerificationRepository;
import com.fortunaball.ticket.domain.InvalidTicketQrException;
import com.fortunaball.ticket.domain.ParsedTicket;
import com.fortunaball.ticket.domain.TicketFingerprint;
import com.fortunaball.ticket.domain.TicketQrImageReader;
import com.fortunaball.ticket.domain.TicketQrParser;
import com.fortunaball.verify.domain.MatchResult;
import com.fortunaball.verify.domain.VerificationResultStatus;
import com.fortunaball.verify.domain.WinningRankRule;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 익명 당첨 인증 등록 (API-16). 당첨 용지 사진 + 발급코드를 받아 "검수 대기"로 저장한다.
 * 📚 배운 내용: @Service, TransactionTemplate(코드로 트랜잭션 범위를 직접 정하는 방법)
 *
 * 흐름 (앞에서 걸러질수록 DB 와 파일을 건드리지 않는다)
 *  [트랜잭션 밖]  1) 조회 횟수 제한  2) 동의 확인  3) 발급코드·댓글 형식  4) 사진 검사(JPG/PNG·용량)
 *                 5) 사진에서 QR 읽기  6) QR 해석(진짜 로또 용지 형식인지)
 *  [트랜잭션 안]  7) 발급코드 조회  8) 용지 회차 = 발급 대상 회차?  9) 추첨 결과가 있는지
 *                 10) 용지 번호가 발급 번호에 있는지  11) 그 번호가 당첨(1~5등)인지  12) 중복 등록 아닌지
 *                 13) 검증 결과 저장 → 사진 저장 → 인증 기록(검수 대기) 저장
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 회차와 등수는 사용자가 입력하지 않는다. 용지 QR 과 서버의 당첨번호 비교로만 정한다. (사진 속 글자는 읽지 않는다. AI 이미지 분석 없음)
 *  - QR 이미지 읽기는 시간이 걸려서 트랜잭션(DB 연결을 붙잡는 구간) 밖에서 한다.
 *  - 중복 등록은 ①먼저 확인해서 친절한 문구로 거절하고 ②동시에 두 번 눌러 확인을 빠져나간 경우는 DB 의 UNIQUE 가 막는다.
 *    ②는 예외를 잡아 다시 확인한 뒤 ①과 같은 문구로 바꿔 준다. (그래서 트랜잭션 "밖"에서 예외를 잡는다)
 *  - 사진 파일은 트랜잭션 안에서 저장한다. 이후 단계가 실패해 롤백되면 방금 쓴 파일도 지워진다. (PrivateFileStorage 참고)
 *  - 로그와 오류 상세에는 QR 원문·일련번호·발급코드·원래 파일 이름을 남기지 않는다.
 */
@Service
@EnableConfigurationProperties(RateLimitProperties.class)
public class ProofRegisterService {

    private static final Logger log = LoggerFactory.getLogger(ProofRegisterService.class);

    static final String RATE_PURPOSE = "proof-register";
    static final int COMMENT_MAX = 300;

    /** 발급코드 형식 (IssueCodeGenerator 와 같은 글자 집합, 12자. 조회 API 와 같은 규칙) */
    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Z2-9]{12}$");

    static final String MSG_CONSENT = "사진 저장·검수에 동의해 주세요.";
    static final String MSG_NO_ISSUE = "발급 내역을 찾을 수 없어요. 발급코드를 확인해 주세요.";
    static final String MSG_NO_QR = "사진에서 QR을 읽지 못했어요. QR이 잘 보이게 다시 찍어 주세요.";
    static final String MSG_BAD_QR = "로또 용지 QR이 아니에요. QR이 잘 보이게 다시 확인해 주세요.";
    static final String MSG_NOT_DRAWN = "아직 추첨 결과가 없는 회차예요. 추첨 후 다시 등록해 주세요.";
    static final String MSG_NO_MATCH = "이 용지에는 발급코드의 번호가 없어요. 발급코드와 용지를 확인해 주세요.";
    static final String MSG_NOT_WINNER = "당첨된 번호가 없어서 인증할 수 없어요.";
    static final String MSG_DUP_ISSUE = "이미 인증이 등록된 발급코드예요.";
    static final String MSG_DUP_TICKET = "이미 등록된 용지예요.";
    static final String MSG_DONE = "인증을 등록했어요. 검수 후 당첨 기록에 공개돼요.";

    private final RequestRateLimiter rateLimiter;
    private final RateLimitProperties rateProps;
    private final UploadedImageValidator imageValidator;
    private final TicketQrImageReader qrReader;
    private final NumberIssueRepository issueRepo;
    private final IssueNumberRepository numberRepo;
    private final DrawRoundRepository drawRepo;
    private final WinningVerificationRepository winningRepo;
    private final AnonymousVerificationRepository proofRepo;
    private final PrivateFileStorage storage;
    private final TransactionTemplate tx;

    public ProofRegisterService(RequestRateLimiter rateLimiter, RateLimitProperties rateProps,
                                UploadedImageValidator imageValidator, TicketQrImageReader qrReader,
                                NumberIssueRepository issueRepo, IssueNumberRepository numberRepo,
                                DrawRoundRepository drawRepo, WinningVerificationRepository winningRepo,
                                AnonymousVerificationRepository proofRepo, PrivateFileStorage storage,
                                TransactionTemplate tx) {
        this.rateLimiter = rateLimiter;
        this.rateProps = rateProps;
        this.imageValidator = imageValidator;
        this.qrReader = qrReader;
        this.issueRepo = issueRepo;
        this.numberRepo = numberRepo;
        this.drawRepo = drawRepo;
        this.winningRepo = winningRepo;
        this.proofRepo = proofRepo;
        this.storage = storage;
        this.tx = tx;
    }

    /**
     * @param issueCode        발급코드 (앞뒤 공백·소문자는 정리한다)
     * @param consent          사진 저장·검수 동의 여부. false 면 거절
     * @param comment          한마디(선택). 300자까지
     * @param originalFileName 올린 파일 이름(기록용)
     * @param photo            사진 내용
     * @param clientKey        접속지(IP) 구분값
     */
    public ProofRegisterResponse register(String issueCode, boolean consent, String comment,
                                          String originalFileName, byte[] photo, String clientKey) {
        rateLimiter.check(RATE_PURPOSE, clientKey, rateProps.proofRegisterPerMinute());

        if (!consent) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, MSG_CONSENT, "동의하지 않음");
        }
        String code = normalizeCode(issueCode);
        String cleanComment = cleanComment(comment);

        imageValidator.validate(photo);                 // JPG/PNG·용량. 아니면 여기서 거절
        ParsedTicket ticket = readTicket(photo);        // 사진 → QR → 용지 내용
        String ticketHash = TicketFingerprint.of(ticket);

        try {
            return tx.execute(status -> persist(code, ticket, ticketHash, cleanComment, originalFileName, photo));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 발급코드/용지를 등록해서 DB UNIQUE 에 걸린 경우 → 어느 쪽인지 다시 확인해 안내 문구로 바꾼다.
            throw duplicateAfterRace(code, ticketHash, e);
        }
    }

    // ---- 트랜잭션 밖 도우미 ----

    private String normalizeCode(String issueCode) {
        String code = issueCode == null ? "" : issueCode.trim().toUpperCase();
        if (!CODE_FORMAT.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, MSG_NO_ISSUE,
                    "발급코드 형식 오류: length=" + (issueCode == null ? 0 : issueCode.length()));
        }
        return code;
    }

    /** 앞뒤 공백·제어문자(줄바꿈 제외)를 지운다. 비면 null, 300자를 넘으면 거절. */
    private String cleanComment(String comment) {
        if (comment == null) {
            return null;
        }
        String c = comment.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        if (c.isEmpty()) {
            return null;
        }
        if (c.length() > COMMENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "한마디는 " + COMMENT_MAX + "자까지 쓸 수 있어요.", "댓글 길이 초과: " + c.length());
        }
        return c;
    }

    private ParsedTicket readTicket(byte[] photo) {
        String qrText = qrReader.read(photo)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, MSG_NO_QR, "사진에서 QR 을 읽지 못함"));
        ParsedTicket ticket;
        try {
            ticket = TicketQrParser.parse(qrText);
        } catch (InvalidTicketQrException e) {
            // e.getMessage() 에는 틀린 부분의 종류만 있고 QR 원문은 없다
            throw new BusinessException(ErrorCode.INVALID_INPUT, MSG_BAD_QR, "QR 해석 실패: " + e.getMessage());
        }
        if (ticket.serial() == null || ticket.serial().isBlank()) {
            // 용지를 구별할 값이 없으면 같은 용지 중복 등록을 막을 수 없어서 받지 않는다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, MSG_BAD_QR, "QR 에 용지 식별값이 없음");
        }
        return ticket;
    }

    // ---- 트랜잭션 안 ----

    private ProofRegisterResponse persist(String code, ParsedTicket ticket, String ticketHash, String comment,
                                          String originalFileName, byte[] photo) {
        NumberIssue issue = issueRepo.findByIssueCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, MSG_NO_ISSUE, "발급코드 없음"));

        if (ticket.roundNo() != issue.getTargetRoundNo()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이 발급코드는 " + issue.getTargetRoundNo() + "회차용이에요. 용지의 회차가 달라요.",
                    "회차 불일치: 용지=" + ticket.roundNo() + ", 발급=" + issue.getTargetRoundNo());
        }

        DrawRound draw = drawRepo.findByRoundNo(ticket.roundNo()).orElseThrow(
                () -> new BusinessException(ErrorCode.CONFLICT, MSG_NOT_DRAWN, "draw_round 없음: round=" + ticket.roundNo()));

        // 용지 번호 중 "발급 번호와 같은 조합"을 찾는다.
        List<IssueNumber> issued = numberRepo.findByNumberIssueIdOrderByGameOrderAsc(issue.getId());
        List<IssueNumber> matched = issued.stream()
                .filter(ig -> ticket.games().stream().anyMatch(tg -> tg.numbers().equals(ig.numbers())))
                .toList();
        if (matched.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, MSG_NO_MATCH, "용지 번호와 일치하는 발급 번호 없음");
        }

        // 그중 당첨(1~5등)인 조합만 모은다. 등수는 서버가 번호를 비교해서만 정한다.
        List<Integer> winningNumbers = draw.getNumbers();
        int bonus = draw.getBonusNumber();
        List<IssueNumber> winners = new ArrayList<>();
        List<MatchResult> results = new ArrayList<>();
        for (IssueNumber g : matched) {
            MatchResult r = WinningRankRule.evaluate(g.numbers(), winningNumbers, bonus);
            if (r.rank() != null) {
                winners.add(g);
                results.add(r);
            }
        }
        if (winners.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, MSG_NOT_WINNER, "일치한 번호가 모두 낙첨");
        }

        // 중복 등록 확인 (반려된 건은 제외)
        if (proofRepo.existsByNumberIssueIdAndReviewStatusNot(issue.getId(), ReviewStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.CONFLICT, MSG_DUP_ISSUE, "발급코드 중복 등록");
        }
        if (proofRepo.existsByTicketHashAndReviewStatusNot(ticketHash, ReviewStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.CONFLICT, MSG_DUP_TICKET, "같은 용지 중복 등록");
        }

        // 여기부터 저장. 당첨 검증 결과는 당첨 기록(API-17)이 쓰므로 당첨 조합만 저장해 둔다. (이미 있으면 건너뜀)
        for (int i = 0; i < winners.size(); i++) {
            MatchResult r = results.get(i);
            winningRepo.insertIfAbsent(winners.get(i).getId(), draw.getRoundNo(), r.matchCount(), r.bonusMatched(),
                    r.rank(), VerificationResultStatus.VERIFIED.name());
        }
        FileUpload file = storage.save(originalFileName, photo);
        AnonymousVerification saved = proofRepo.saveAndFlush(
                AnonymousVerification.register(issue.getId(), file.getId(), code, ticketHash, comment));

        log.info("당첨 인증 등록: verificationId={}, round={}", saved.getId(), draw.getRoundNo());
        return new ProofRegisterResponse(saved.getId(), saved.getReviewStatus().name(), MSG_DONE);
    }

    /** DB UNIQUE 위반 → 안내 문구로 바꾼다. 원인을 못 찾으면 원래 예외를 그대로 올린다. */
    private RuntimeException duplicateAfterRace(String code, String ticketHash, DataIntegrityViolationException e) {
        boolean issueTaken = issueRepo.findByIssueCode(code)
                .map(i -> proofRepo.existsByNumberIssueIdAndReviewStatusNot(i.getId(), ReviewStatus.REJECTED))
                .orElse(false);
        if (issueTaken) {
            return new BusinessException(ErrorCode.CONFLICT, MSG_DUP_ISSUE, "발급코드 중복 등록(동시 요청)");
        }
        if (proofRepo.existsByTicketHashAndReviewStatusNot(ticketHash, ReviewStatus.REJECTED)) {
            return new BusinessException(ErrorCode.CONFLICT, MSG_DUP_TICKET, "같은 용지 중복 등록(동시 요청)");
        }
        return e;
    }
}
