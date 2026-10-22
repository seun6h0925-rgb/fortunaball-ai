import jsQR from 'jsqr'

// 사진 한 장에서 QR 글자를 읽는다. (서버로 사진을 보내지 않고 이 기기 안에서만 처리한다)
// 용지 사진은 QR 이 작게 찍히는 일이 많아서, 크기를 달리해서 여러 번 시도한다.

const TRY_WIDTHS = [1600, 2400, 1000]

/** 이미지 파일 → QR 글자. 못 읽으면 null */
export async function readQrFromFile(file) {
  const bitmap = await createImageBitmap(file)
  try {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    for (const w of TRY_WIDTHS) {
      const scale = Math.min(1, w / Math.max(bitmap.width, bitmap.height))
      canvas.width = Math.round(bitmap.width * scale)
      canvas.height = Math.round(bitmap.height * scale)
      ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
      const { data, width, height } = ctx.getImageData(0, 0, canvas.width, canvas.height)
      const found = jsQR(data, width, height, { inversionAttempts: 'attemptBoth' })
      if (found?.data) return found.data
    }
    return null
  } finally {
    bitmap.close?.()
  }
}
