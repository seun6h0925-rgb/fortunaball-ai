package com.fortunaball.ticket.domain;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * 사진(JPG/PNG) 속 로또 용지 QR 을 읽어 "QR 문자열"로 돌려준다. (6번 당첨 인증 등록에서 사용)
 *
 * <p>역할은 여기까지다. 읽은 문자열이 진짜 로또 용지 QR 인지, 회차·번호가 맞는지는
 * {@link TicketQrParser} 가 판단한다. (이미지 읽기와 내용 해석을 일부러 나눴다)</p>
 *
 * <p>스마트폰 사진은 크기가 크고 QR 이 작게 찍혀서, 원본 크기로 한 번에 못 읽는 일이 많다.
 * 그래서 [원본 → 1600px → 1000px] 로 줄여 가며, 이진화 방식 2가지로 각각 시도한다.</p>
 *
 * <p>못 읽으면 예외 대신 {@code Optional.empty()} 를 돌려준다. 사진에 QR 이 없거나 흐린 것은
 * 사용자가 흔히 겪는 정상적인 경우이기 때문이다. 안내 문구는 호출하는 서비스가 정한다.</p>
 */
@Component
public class TicketQrImageReader {

    /** 이보다 픽셀이 많은 사진은 읽지 않는다. (용량은 작아도 픽셀이 엄청 큰 "압축 폭탄" 이미지 방어) */
    static final long MAX_PIXELS = 50_000_000L;

    /** 시도할 긴 변 크기. 0 = 원본 그대로. */
    private static final int[] TRY_SIDES = {0, 1600, 1000};

    public Optional<String> read(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        BufferedImage image = decodeImage(imageBytes);
        if (image == null) {
            return Optional.empty();
        }
        return read(image);
    }

    public Optional<String> read(BufferedImage image) {
        for (int side : TRY_SIDES) {
            BufferedImage target = (side == 0) ? image : shrink(image, side);
            if (target == null) {
                continue; // 이미 이 크기보다 작아서 줄일 필요가 없다
            }
            Optional<String> text = decode(target);
            if (text.isPresent()) {
                return text;
            }
        }
        return Optional.empty();
    }

    /** 이미지 바이트 → BufferedImage. 이미지가 아니거나 너무 크면 null. */
    private BufferedImage decodeImage(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 긴 변이 maxSide 가 되도록 줄인다. 이미 작으면 null (줄일 필요 없음). */
    private BufferedImage shrink(BufferedImage src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= maxSide) {
            return null;
        }
        double ratio = (double) maxSide / longSide;
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 한 크기의 이미지를 [Hybrid → GlobalHistogram] 순서로 읽어 본다. */
    private Optional<String> decode(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        LuminanceSource source = new RGBLuminanceSource(w, h, pixels);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

        BinaryBitmap[] bitmaps = {
                new BinaryBitmap(new HybridBinarizer(source)),
                new BinaryBitmap(new GlobalHistogramBinarizer(source))
        };
        for (BinaryBitmap bitmap : bitmaps) {
            try {
                // MultiFormatReader 는 여러 스레드가 함께 쓰면 안 되어서 매번 새로 만든다.
                return Optional.of(new MultiFormatReader().decode(bitmap, hints).getText());
            } catch (NotFoundException e) {
                // 이 방식으로는 못 찾음 → 다음 방식
            }
        }
        return Optional.empty();
    }
}
