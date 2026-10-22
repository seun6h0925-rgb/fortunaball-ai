import { api } from '../../api/http.js'

// AI 로또 애널리스트(UI-05)가 쓰는 서버 기능.

/** 질문 글자 수 제한 (서버와 같게 맞춘다: 1~300자) */
export const MAX_QUESTION = 300

/**
 * API-07 질문하기
 * 보내는 값: { question, history[{ role:'user'|'assistant', content }] }  (history 는 화면이 들고 있다가 보낸다. 서버는 저장하지 않는다)
 * 받는 값: { questionType, policyCode, message, answer, generated, evidence{basis, items[{label,value}]}, references,
 *           notice, remainingToday, historyText, suggestions, action, charged }
 *  - generated : 번호를 만들었을 때만. 번호 생성(API-03)과 같은 모양 { confirmToken, games[[6]], targetRound, basisRound, expiresAt ... }
 *  - 고정 응답(인사·사용법·정책)이면 answer 는 null 이고 message 에 문구가 있다.
 */
export const askAnalyst = (question, history) => api.post('/api/ai/analyst', { question, history })

/** API-64 오늘 남은 무료 횟수 (횟수를 쓰지 않는다): { unlimited, dailyFreeLimit, usedToday, remainingToday, resetAt } */
export const fetchAiUsage = (signal) => api.get('/api/ai/usage', { signal })

/** 서버가 기억하는 대화는 없다. 이전 대화는 최근 몇 개만 다시 보내면 되므로 화면이 6개로 잘라서 보낸다. */
export const HISTORY_LIMIT = 6

/** "이렇게 물어보세요" 예시. 누르면 입력창에 들어간다. (목업 문구) */
export const GUIDE_ITEMS = [
  {
    title: '내 번호 분석',
    example: '3, 14, 22, 35, 38, 44 분석해줘',
    desc: '홀짝, 구간, 최근 출현 횟수를 과거 통계로 설명해 드려요.',
  },
  {
    title: '번호 생성',
    example: '7 넣어서 3게임 뽑아줘',
    desc: '통계 분석으로 번호를 만든 뒤 "확정할게"라고 말하면 발급이 확정돼요.',
  },
  {
    title: '통계 질문',
    example: '최근 10회에서 가장 많이 나온 번호는?',
    desc: '',
  },
  {
    title: '주변 당첨 판매점 안내',
    example: '여기 주변에서 당첨 제일 많은 판매점 알려줘',
    desc: '위치 동의가 필요해요. 위치는 저장되지 않고, 답변의 "위치 보기"를 누르면 지도로 이동해요.',
  },
]

/**
 * 사용법 창의 "자세한 사용법". 항목 하나 = 제목 + 설명 줄들. 문구를 바꿀 때는 여기만 고친다.
 * (무료 횟수·임시 결과 유지 시간 같은 숫자는 서버 설정이라, 바뀌면 이 문구도 같이 확인한다)
 */
export const GUIDE_DETAILS = [
  {
    title: '내 번호 분석하기',
    lines: [
      '번호 6개를 적고 "분석해줘"라고 보내세요. 홀짝, 구간, 최근 출현 횟수를 과거 통계로 설명해 드려요.',
      '발급받은 번호는 발급 코드를 적어도 돼요. 예) FB7K29QXM4AB 분석해줘',
      '내 발급 내역의 [분석 보기]를 누르면 이 화면이 열리고 발급 코드가 입력창에 미리 들어가 있어요. 보내기만 하면 돼요.',
    ],
  },
  {
    title: '번호 만들기',
    lines: [
      '게임 수와 포함·제외 번호를 말로 정해요. 예) 7 넣어서 3게임 뽑아줘 / 7, 21 빼고 5게임 뽑아줘',
      '만든 번호는 아직 저장되지 않은 임시 결과예요. 30분 안에 "확정할게"라고 말하거나 아래 [확정할게]를 누르면 발급이 확정되고 발급 코드를 받아요.',
      '마음에 들지 않으면 [다시 생성]이나 [다시 뽑아줘]를 누르세요. 같은 조건으로 새 번호를 만들어요. (새 질문으로 계산돼요)',
    ],
  },
  {
    title: '통계 물어보기',
    lines: [
      '회차나 기간을 넣어 물어보세요. 예) 1245회 번호 분석해줘 / 최근 50회에서 가장 많이 나온 번호는?',
      '숫자는 서버가 계산한 통계이고, AI는 그 결과를 쉬운 말로 풀어 써요. 말풍선 아래 [통계 근거 보기]에서 어떤 자료를 봤는지 확인할 수 있어요.',
    ],
  },
  {
    title: '주변 당첨 판매점',
    lines: [
      '위치 동의가 필요해요. 위치는 저장되지 않고 AI에게도 전달되지 않아요.',
      '답변의 [위치 보기]를 누르면 지도로 이동해요.',
    ],
  },
  {
    title: '무료 횟수',
    lines: [
      '질문 1번에 무료 횟수 1회가 차감돼요. 남은 횟수는 대화창 위쪽에서 볼 수 있고, 다음 날 0시에 다시 채워져요.',
      '[통계 근거 보기]와 [확정할게]는 횟수가 차감되지 않아요.',
      '답변을 받지 못하고 오류가 난 경우에는 횟수를 돌려드려요.',
    ],
  },
  {
    title: '알아두세요',
    lines: [
      '질문은 300자 이내로 적어 주세요. 답변은 500자 이내로 제공돼요.',
      '대화는 서버에 저장되지 않아요. 화면을 새로고침하거나 나가면 대화가 사라지니, 필요한 번호는 확정해서 발급 코드를 남겨 주세요.',
      '모든 수치는 과거에 나온 결과를 정리한 사실이고, 생성 번호는 통계 기반 참고용이에요. 당첨을 보장하지 않아요.',
    ],
  },
]
