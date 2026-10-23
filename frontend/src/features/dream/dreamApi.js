import { api } from '../../api/http.js'

// AI 로또 꿈풀이(UI-06)가 쓰는 서버 기능.

export const DREAM_MIN = 5
export const DREAM_MAX = 300
/** 이 길이 이상으로 적은 글은, 질문에 대한 답이 아니라 새 꿈 이야기로 보고 처음부터 보낸다 */
export const NEW_STORY_LENGTH = 20

/**
 * API-08 꿈풀이 (대화로 이어진다)
 * 처음: { dream }
 * 이어서 답할 때: { dream(처음 꿈 그대로), reply, step, keep, gameCount, confirmToken }  ← step·keep·gameCount·confirmToken 은 직전 응답 값을 그대로 돌려보낸다
 * 꿈과 상관없는 질문처럼 새 이야기로 보내는 말에는 { dream(방금 한 말), ongoing(이야기 중인 꿈), step, history }. 모든 요청에 history(최근 대화 6개 이하 [{ role, content }])를 같이 보낸다.
 * 받는 값: { matchResult, step, policyCode, message, question, symbols[{keyword, interpretations[{situation, summary, polarity, region}]}],
 *           interpretation, numbers[{number, origin}], games[[{number, origin}]], keep, gameCount, confirmToken, tokenTtlMinutes,
 *           targetRound, action('ISSUE'|'OPEN_GUIDE'), quickReplies[], suggestions[], charged, basisNotice, notice, remainingToday }
 * 서버는 꿈 내용을 저장하지 않는다. 그래서 화면이 처음 꿈을 들고 있다가 이어지는 요청에도 다시 보낸다.
 */
export const askDream = (body) => api.post('/api/ai/dream', body)

/** 서버가 사용자의 답을 기다리는 단계들. 이 단계에서 짧게 적은 글은 "답"으로 보낸다. */
export const AWAITING_STEPS = ['ASK_STAT_NUMBERS', 'ASK_FILL', 'ASK_OVERFLOW', 'CONFIRM']

/** 번호 아래에 붙이는 출처 이름 */
export function originLabel(origin) {
  switch (origin) {
    case 'DREAM_TEXT':
      return '꿈 속 숫자'
    case 'SMORFIA':
    case 'CHARADA':
      return '해외 전통'
    case 'STAT':
      return '통계'
    default:
      return '서비스 연결'
  }
}

/** 상징 해석에 붙이는 출처 이름: KR = 한국 전통, 그 밖(GLOBAL) = 해외 전통 */
export const regionLabel = (region) => (region === 'KR' ? '한국 전통' : '해외 전통')

/** 칩에 보여 줄 이름. 서버가 말로 받는 문구는 그대로 보내되, 화면에는 목업의 짧은 이름을 쓴다. */
export const CHIP_LABELS = { '다시 만들어 주세요': '다시 생성' }
export const CHIP_SPOKEN = { '다시 만들어 주세요': '다시 생성해줘' }

/** 입력창 위 예시 칩 (누르면 입력창에 들어간다) */
export const DREAM_EXAMPLES = ['물에 빠지는 꿈', '하늘을 나는 꿈', '뱀을 본 꿈']

/** "이렇게 물어보세요" (목업 문구) */
export const DREAM_GUIDE_ITEMS = [
  {
    title: '꿈 이야기 들려주기',
    example: '큰 돼지가 집으로 들어오는 꿈을 꿨어요',
    desc: '꿈 속 사물·동물·장면을 5~300자로 적어 주세요. 꿈에 숫자(1~45)가 나오면 번호 후보로 써요.',
  },
  {
    title: '풀이와 번호 보기',
    example: '상징별 해석과 번호 6개가 함께 나와요',
    desc: '한국 전통 꿈풀이를 먼저 참고하고, 한국 자료가 없는 상징만 해외 자료로 보완해요. 번호는 상징 목록과 통계에서 가져오고, AI가 임의로 만들지 않아요.',
    fixed: true,
  },
  {
    title: '확정·다시 생성',
    example: '"확정할게요"로 발급하거나 "다시 생성"을 눌러 보세요',
    desc: '번호의 홀짝 비율, 구간, 최근 출현 횟수와 참고한 자료를 볼 수 있어요. 횟수는 차감되지 않아요.',
    fixed: true,
  },
  {
    title: '부족한 번호 통계로 채우기',
    example: '"네, 채워 주세요"를 눌러 보세요',
    desc: '번호가 6개보다 적으면 통계로 채워요. 1~3개일 때는 먼저 동의를 물어보고, 채운 번호에는 통계 표시가 붙어요.',
    fixed: true,
  },
]

export const DREAM_GUIDE_LEAD = '예시를 누르면 입력창에 바로 들어가요. 오늘 남은 무료 횟수는 대화창 위쪽에서 볼 수 있어요.'
export const DREAM_GUIDE_NOTE =
  '꿈풀이는 재미로 참고해 주세요. 통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.'

/** 자세한 사용법 (서버 동작 기준: DreamMessages·DreamService) */
export const DREAM_GUIDE_DETAILS = [
  {
    title: '꿈 이야기 들려주기',
    lines: [
      '기억나는 장면을 300자 이내로 편하게 적어 주세요. 예) 큰 돼지가 집으로 들어오는 꿈을 꿨어요',
      '꿈 이야기 말고 편하게 말을 걸어도 AI가 짧게 답하고 꿈 이야기로 이끌어 줘요. 꿈이 기억나지 않는다고 해도 괜찮아요.',
      '꿈에 숫자(1~45)가 나왔다면 함께 적어 주세요. 번호 후보로 써요.',
    ],
  },
  {
    title: '풀이와 번호',
    lines: [
      '꿈에서 찾은 상징을 풀이하고, 그 상징에서 나온 번호를 한 게임으로 정리해 드려요.',
      '한국 전통 꿈풀이를 먼저 참고하고, 한국 자료가 없는 상징만 해외 전통 자료로 보완해요. 번호 아래에 어디서 온 번호인지 표시돼요.',
      '번호는 정해진 상징 목록, 꿈에 적은 숫자, 통계에서만 가져와요. AI가 임의로 만들지 않아요.',
    ],
  },
  {
    title: '번호 개수에 따라 달라져요',
    lines: [
      '6개가 채워지면 바로 확정 여부를 물어봐요.',
      '4~5개면 모자란 번호를 통계로 채워서 보여 드려요.',
      '1~3개면 "나머지를 통계로 채워도 될까요?"를 먼저 물어봐요. [네, 채워 주세요]를 누르면 채워요.',
      '꿈 번호가 하나도 없으면 "통계 번호를 만들어 볼까요?"를 물어봐요.',
      '6개를 넘으면 몇 게임으로 나눌지, 통계로 6개만 고를지, 뺄 번호가 있는지 물어봐요. 예) 3게임으로 나눠 주세요 / 17 빼 주세요',
    ],
  },
  {
    title: '확정하거나 다시 만들기',
    lines: [
      '"확정할게요"라고 말하거나 [확정할게요]를 누르면 발급 코드가 만들어져요. 확정에는 횟수가 차감되지 않아요.',
      '[다시 생성]은 꿈 상징 번호는 그대로 두고, 통계로 채운 번호만 새로 만들어요. 이때 무료 횟수가 1회 사용돼요.',
      '[분석 보기]를 누르면 AI 애널리스트가 이 번호를 분석해 줘요.',
    ],
  },
  {
    title: '무료 횟수',
    lines: [
      'AI가 답할 때마다 무료 횟수가 1회 차감돼요. 꿈과 조금 벗어난 대화도 AI가 답하면 차감돼요.',
      '인사, 사용법 같은 고정 안내와 확정, 도와드릴 수 없는 주제 안내는 차감되지 않아요.',
      '남은 횟수는 대화창 위쪽에서 볼 수 있고, 다음 날 0시에 다시 채워져요.',
      '답변을 받지 못하고 오류가 난 경우에는 횟수를 돌려드려요.',
    ],
  },
  {
    title: '알아두세요',
    lines: [
      '꿈풀이는 재미와 참고를 위한 풀이예요. 당첨을 알려 드리지 않아요.',
      '꿈 내용은 서버에 저장되지 않아요. 화면을 새로고침하거나 나가면 대화가 사라지니, 필요한 번호는 확정해서 발급 코드를 남겨 주세요.',
      '답변은 500자 이내로 제공돼요.',
    ],
  },
]
