import { api } from '../../api/http.js'

// 내 용지 확인하기(UI-08)가 쓰는 서버 기능. 읽기 전용이라 서버에 저장하지 않는다.

/**
 * API-63 용지 QR 당첨 확인. 사진은 보내지 않고 브라우저가 QR 에서 읽은 글자(qrText)만 보낸다.
 * 받는 값: { roundNo, drawn, drawDate, winningNumbers[6]|null, bonusNumber|null, bestRank|null, bestRankLabel|null, message,
 *           games[{ line, kindLabel, numbers[6], matchCount, bonusMatched, rank, rankLabel, matchedNumbers }] }
 * 아직 추첨 전인 회차는 오류가 아니라 drawn=false 와 안내 문구(message)로 온다.
 */
export const checkTicket = (signal, qrText) => api.post('/api/tickets/check', { qrText }, { signal })
