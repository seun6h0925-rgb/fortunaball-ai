package com.fortunaball.common.file;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 비공개 파일 보관소. 사진을 서버 폴더(UPLOAD_DIR)에 저장하고 읽고 지운다.
 *
 * 규칙
 *  1) 폴더는 프로젝트(Git) 밖의 절대 경로이고, 웹 주소로 바로 열 수 없다. 사진은 관리자 검수 API 를 거쳐서만 읽는다.
 *  2) 저장 파일 이름은 서버가 만든 임의 값(UUID 32자 + 확장자)이다. 사용자가 올린 이름은 DB 기록용으로만 쓴다.
 *     → 파일 이름으로 폴더 밖을 건드리는 공격(../ 등)이 아예 불가능하다.
 *  3) 저장 도중 DB 기록이 실패하거나, 이 메서드를 부른 쪽의 처리(트랜잭션)가 롤백되면 방금 쓴 파일도 지운다.
 *     → 기록 없는 파일이 폴더에 쌓이지 않는다.
 *
 * ⚠ 설정이 잘못되면(UPLOAD_DIR 비어 있음·상대 경로·만들 수 없음) 사용자에게는 "잠시 후 다시 시도" 안내만 나가고,
 *    원인은 로그의 detail 에 남는다. 서버를 켤 때도 한 번 점검해서 로그에 알려 준다.
 */
@Service
@EnableConfigurationProperties(UploadProperties.class)
public class PrivateFileStorage {

    private static final Logger log = LoggerFactory.getLogger(PrivateFileStorage.class);

    /** 우리가 만드는 저장 파일 이름 모양. 이 모양이 아니면 읽기·삭제를 거부한다. */
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png)$");

    private static final int ORIGINAL_NAME_MAX = 255;

    private final UploadProperties props;
    private final UploadedImageValidator validator;
    private final FileUploadRepository repository;

    public PrivateFileStorage(UploadProperties props, UploadedImageValidator validator,
                              FileUploadRepository repository) {
        this.props = props;
        this.validator = validator;
        this.repository = repository;
    }

    /** 서버를 켤 때 업로드 폴더 설정을 점검해서 로그로 알린다. (문제가 있어도 서버는 계속 켜진다) */
    @PostConstruct
    void checkAtStartup() {
        try {
            Path dir = uploadDir();
            log.info("사진 업로드 폴더: {} (사진 1장 최대 {}MB)", dir, props.maxSizeMb());
        } catch (BusinessException e) {
            log.warn("사진 업로드 폴더 설정에 문제가 있어요. 당첨 인증 사진 등록이 동작하지 않아요. 원인: {}", e.getDetail());
        }
    }

    /**
     * 사진을 검사하고 폴더에 저장한 뒤 file_upload 기록을 만든다.
     *
     * @param originalName 사용자가 올린 파일 이름(기록용). 경로 구분자·제어문자는 지운다.
     * @param bytes        사진 내용
     */
    @Transactional
    public FileUpload save(String originalName, byte[] bytes) {
        UploadedImageValidator.DetectedImage image = validator.validate(bytes);
        Path dir = uploadDir();

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + image.extension();
        Path target = dir.resolve(storedName);

        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "사진을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.",
                    "사진 파일 쓰기 실패: " + e.getClass().getSimpleName() + " / 폴더=" + dir);
        }

        // 이 메서드를 부른 쪽의 트랜잭션이 롤백되면 방금 쓴 파일도 지운다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(target);
                    }
                }
            });
        }

        try {
            return repository.save(FileUpload.uploaded(
                    sanitizeName(originalName), storedName, target.toString(),
                    bytes.length, image.extension(), image.contentType()));
        } catch (RuntimeException e) {
            deleteQuietly(target);   // DB 기록이 실패하면 파일도 지운다.
            throw e;
        }
    }

    /** 저장된 사진 내용을 읽는다. (관리자 검수 화면용) */
    @Transactional(readOnly = true)
    public byte[] read(FileUpload file) {
        if (file.getUploadStatus() != FileUploadStatus.UPLOADED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사진을 찾을 수 없어요.",
                    "사진 상태=" + file.getUploadStatus() + ", id=" + file.getId());
        }
        Path path = resolve(file.getStoredFileName());
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사진을 찾을 수 없어요.",
                    "사진 파일 읽기 실패: " + e.getClass().getSimpleName() + ", id=" + file.getId());
        }
    }

    /** 파일을 지우고 기록 상태를 DELETED 로 바꾼다. (기록은 남긴다) */
    @Transactional
    public void delete(FileUpload file) {
        Path path = resolve(file.getStoredFileName());
        deleteQuietly(path);
        file.markDeleted();
        repository.save(file);
    }

    // ---- 내부 도우미 ----

    /** 설정된 폴더를 점검하고(없으면 만들고) 돌려준다. */
    private Path uploadDir() {
        String raw = props.dir();
        if (raw == null || raw.isBlank()) {
            throw unavailable("UPLOAD_DIR 가 비어 있음. 저장소 루트 .env 에 UPLOAD_DIR 를 적어 주세요.");
        }
        if (raw.chars().anyMatch(Character::isISOControl)) {
            // .env 에서 C:\fortuna... 처럼 역슬래시를 쓰면 \f 가 줄바꿈 문자로 읽혀 경로가 깨진다.
            throw unavailable("UPLOAD_DIR 에 제어문자가 섞여 있음. .env 에서는 역슬래시(\\) 대신 슬래시(/)로 적어 주세요.");
        }
        Path dir;
        try {
            dir = Path.of(raw.trim());
        } catch (InvalidPathException e) {
            throw unavailable("UPLOAD_DIR 경로 형식이 올바르지 않음.");
        }
        if (!dir.isAbsolute()) {
            throw unavailable("UPLOAD_DIR 는 절대 경로여야 함. (예: C:/myws/semi_project/fortuna-data-uploads)");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw unavailable("UPLOAD_DIR 폴더를 만들 수 없음: " + e.getClass().getSimpleName() + " / " + dir);
        }
        return dir;
    }

    /** 저장 파일 이름 → 실제 경로. 우리가 만든 이름 모양이 아니면 거부한다. */
    private Path resolve(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사진을 찾을 수 없어요.",
                    "저장 파일 이름 형식이 올바르지 않음");
        }
        return uploadDir().resolve(storedName);
    }

    /** 사용자가 올린 이름에서 폴더 경로·제어문자를 지운다. 비면 "photo". */
    static String sanitizeName(String name) {
        if (name == null) {
            return "photo";
        }
        String base = name;
        int cut = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (cut >= 0) {
            base = base.substring(cut + 1);
        }
        base = base.replaceAll("\\p{Cntrl}", "").trim();
        if (base.isEmpty()) {
            return "photo";
        }
        return base.length() > ORIGINAL_NAME_MAX ? base.substring(0, ORIGINAL_NAME_MAX) : base;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 지우지 못해도 흐름은 막지 않는다. 로그에 남겨 나중에 직접 정리할 수 있게 한다.
            log.warn("사진 파일을 지우지 못했어요: {} ({})", path, e.getClass().getSimpleName());
        }
    }

    private BusinessException unavailable(String detail) {
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE.getDefaultMessage(), detail);
    }
}
