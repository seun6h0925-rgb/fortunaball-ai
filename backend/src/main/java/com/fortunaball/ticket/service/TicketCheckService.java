package com.fortunaball.ticket.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.security.RateLimitProperties;
import com.fortunaball.common.security.RequestRateLimiter;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.ticket.domain.InvalidTicketQrException;
import com.fortunaball.ticket.domain.ParsedTicket;
import com.fortunaball.ticket.domain.TicketGame;
import com.fortunaball.ticket.domain.TicketQrParser;
import com.fortunaball.ticket.dto.TicketCheckRequest;
import com.fortunaball.ticket.dto.TicketCheckResponse;
import com.fortunaball.verify.domain.MatchResult;
import com.fortunaball.verify.domain.WinningRankRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 로또 용지 당첨 확인 (API-63). 읽기 전용이라 DB 에 아무것도 저장하지 않는다.
 * 📚 배운 내용: @Service, @Transactional(readOnly = true)
 *
 * 흐름: 조회 횟수 제한 → QR 글자 해석(TicketQrParser) → 그 회차 당첨번호 조회 → 줄마다 등수 계산(WinningRankRule)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 확인은 "우리 서비스가 발급한 번호"와 상관없이 어떤 로또 용지든 확인한다. (발급코드·게스트 토큰을 쓰지 않는다)
 *  - QR 글자(TR 번호·일련번호 포함)는 저장하지도 로그에 남기지도 않는다. 오류 로그에는 틀린 부분의 종류만 남는다.
 *  - 당첨번호가 아직 없는 회차는 오류가 아니라 "drawn = false + 안내 문구"로 정상 응답한다.
 *    단, DB 에 있는 가장 최근 회차보다 "이전" 회차인데 당첨번호가 없으면 데이터 누락이라서 404 로 알린다.
 *  - 등수는 서버가 번호를 비교해서만 정한다. (WinningRankRule — 당첨 검증과 같은 규칙)
 */
@Service
public class TicketCheckService {

    private static final String RATE_PURPOSE = "ticket-check";
    private static final String[] LINE_NAMES = {"A", "B", "C", "D", "E"};

    static final String MSG_NOT_DRAWN = "아직 당첨번호가 등록되지 않은 회차예요. 추첨 후 잠시 뒤에 다시 확인해 주세요.";
    static final String MSG_BAD_QR = "로또 용지 QR이 아니에요. QR이 잘 보이게 다시 확인해 주세요.";

    private final DrawRoundRepository drawRepo;
    private final RequestRateLimiter rateLimiter;
    private final RateLimitProperties rateProps;

    public TicketCheckService(DrawRoundRepository drawRepo,
                              RequestRateLimiter rateLimiter,
                              RateLimitProperties rateProps) {
        this.drawRepo = drawRepo;
        this.rateLimiter = rateLimiter;
        this.rateProps = rateProps;
    }

    /** clientKey 는 접속지(IP) 구분값 */
    @Transactional(readOnly = true)
    public TicketCheckResponse check(TicketCheckRequest request, String clientKey) {
        rateLimiter.check(RATE_PURPOSE, clientKey, rateProps.ticketCheckPerMinute());

        ParsedTicket ticket = parse(request == null ? null : request.qrText());

        DrawRound draw = drawRepo.findByRoundNo(ticket.roundNo()).orElse(null);
        if (draw == null) {
            return notDrawn(ticket);
        }
        return drawn(ticket, draw);
    }

    private ParsedTicket parse(String qrText) {
        try {
            return TicketQrParser.parse(qrText);
        } catch (InvalidTicketQrException e) {
            // e.getMessage() 에는 틀린 부분의 종류만 있고 QR 원문은 없다
            throw new BusinessException(ErrorCode.INVALID_INPUT, MSG_BAD_QR, "QR 해석 실패: " + e.getMessage());
        }
    }

    /** 당첨번호가 없는 회차 */
    private TicketCheckResponse notDrawn(ParsedTicket ticket) {
        int latest = drawRepo.findTopByOrderByRoundNoDesc().map(DrawRound::getRoundNo).orElse(0);
        if (ticket.roundNo() <= latest) {                       // 지난 회차인데 데이터가 없음 = 누락
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "이 회차의 당첨번호 정보를 찾을 수 없어요.",
                    "draw_round 없음: round=" + ticket.roundNo() + ", latest=" + latest);
        }
        List<TicketCheckResponse.Game> games = new ArrayList<>();
        for (int i = 0; i < ticket.games().size(); i++) {
            TicketGame g = ticket.games().get(i);
            games.add(new TicketCheckResponse.Game(lineName(i), g.kind().label(), g.numbers(),
                    null, null, null, null, null));
        }
        return new TicketCheckResponse(ticket.roundNo(), false, null, null, null, null, null, MSG_NOT_DRAWN, games);
    }

    /** 당첨번호가 있는 회차: 줄마다 등수 계산 */
    private TicketCheckResponse drawn(ParsedTicket ticket, DrawRound draw) {
        List<Integer> winning = draw.getNumbers();
        int bonus = draw.getBonusNumber();

        List<TicketCheckResponse.Game> games = new ArrayList<>();
        Integer bestRank = null;
        for (int i = 0; i < ticket.games().size(); i++) {
            TicketGame g = ticket.games().get(i);
            MatchResult r = WinningRankRule.evaluate(g.numbers(), winning, bonus);
            if (r.rank() != null && (bestRank == null || r.rank() < bestRank)) {
                bestRank = r.rank();
            }
            List<Integer> matched = g.numbers().stream().filter(winning::contains).toList();
            games.add(new TicketCheckResponse.Game(lineName(i), g.kind().label(), g.numbers(),
                    r.matchCount(), r.bonusMatched(), r.rank(), rankLabel(r.rank()), matched));
        }
        return new TicketCheckResponse(ticket.roundNo(), true, draw.getDrawDate(), winning, bonus,
                bestRank, rankLabel(bestRank), bestRank == null ? "아쉽지만 이번 용지는 당첨되지 않았어요." : "당첨을 축하해요!", games);
    }

    private static String lineName(int index) {
        return index < LINE_NAMES.length ? LINE_NAMES[index] : String.valueOf(index + 1);
    }

    /** 등수 → 화면 표시 문구. null 이면 낙첨 */
    private static String rankLabel(Integer rank) {
        return rank == null ? "낙첨" : rank + "등";
    }
}
