package com.fortunaball.ticket.domain;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 사진 속 QR 읽기 테스트.
 * - 일반 테스트: 테스트 안에서 QR 그림을 직접 만들어 읽는다. (가짜 일련번호만 사용)
 * - 실제 용지 사진 테스트: 환경변수 TICKET_SAMPLE_PHOTO 에 사진 경로가 있을 때만 실행된다.
 *   사진은 프로젝트 밖에 두고, 저장소(GitHub)에는 절대 올리지 않는다.
 */
class TicketQrImageReaderTest {

    // 실제 용지 구조를 따른 가짜 QR (일련번호는 가짜)
    private static final String FAKE_QR =
            "http://qr.dhlottery.co.kr/?v=1239m030614163442m010203040506m071113222433q081019283744s091217253340100000000112345678";

    private final TicketQrImageReader reader = new TicketQrImageReader();

    @Test
    void 정상_QR_그림을_읽는다() throws IOException {
        byte[] png = toPng(drawQr(FAKE_QR, 400));

        Optional<String> text = reader.read(png);

        assertEquals(FAKE_QR, text.orElse(null));
    }

    @Test
    void 큰_사진_속_작은_QR도_읽는다() throws IOException {
        // 4000x3000 사진에 QR 이 가운데 작게 있는 상황을 흉내 낸다.
        BufferedImage photo = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = photo.createGraphics();
        g.setColor(new Color(120, 120, 120));
        g.fillRect(0, 0, 4000, 3000);
        g.drawImage(drawQr(FAKE_QR, 500), 1750, 1250, null);
        g.dispose();

        Optional<String> text = reader.read(toPng(photo));

        assertEquals(FAKE_QR, text.orElse(null));
    }

    @Test
    void QR_없는_그림은_빈_결과() throws IOException {
        BufferedImage noise = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < noise.getHeight(); y++) {
            for (int x = 0; x < noise.getWidth(); x++) {
                noise.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }

        assertTrue(reader.read(toPng(noise)).isEmpty());
    }

    @Test
    void 이미지가_아닌_파일은_빈_결과() {
        byte[] notImage = "이건 사진이 아니에요".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(reader.read(notImage).isEmpty());
        assertTrue(reader.read(new byte[0]).isEmpty());
        assertTrue(reader.read((byte[]) null).isEmpty());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TICKET_SAMPLE_PHOTO", matches = ".+")
    void 실제_용지_사진에서_QR을_읽고_해석한다() throws IOException {
        byte[] photo = Files.readAllBytes(Path.of(System.getenv("TICKET_SAMPLE_PHOTO")));

        Optional<String> text = reader.read(photo);

        // QR 내용(일련번호 포함)은 출력하지 않는다.
        assertTrue(text.isPresent(), "실제 사진에서 QR 을 읽지 못했어요");
        ParsedTicket ticket = TicketQrParser.parse(text.get());
        assertTrue(ticket.roundNo() >= 1);
        assertTrue(ticket.games().size() >= 1);
    }

    // ---- 테스트용 도우미 ----

    private static BufferedImage drawQr(String text, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.MARGIN, 4));
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return image;
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
