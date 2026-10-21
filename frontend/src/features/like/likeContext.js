import { createContext, useContext } from 'react'

// 좋아요 상태는 화면 전체에서 하나만 둔다. (머리글과 아래 줄에 버튼이 둘 있어도 숫자가 같이 움직임)
export const LikeContext = createContext(null)
export const useLike = () => useContext(LikeContext)
