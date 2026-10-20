package com.fortunaball.common.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 업로드된 파일 1개의 기록. DB 테이블: file_upload
 * 사진 파일 자체는 DB 가 아니라 서버 폴더(UPLOAD_DIR)에 있고, 여기에는 "어떤 파일이 어디에 있는지"만 적는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - storedFileName 이 실제 파일 이름이다. (서버가 만든 임의 이름. 사용자가 올린 이름이 아니다)
 *  - filePath 는 저장 당시의 전체 경로(기록용)다. 실제로 읽을 때는 "지금 설정된 UPLOAD_DIR + storedFileName" 으로 찾는다.
 *    그래서 폴더를 다른 컴퓨터/서버로 옮겨도 .env 의 UPLOAD_DIR 만 바꾸면 계속 쓸 수 있다.
 *  - originalFileName 에는 이름·연락처 같은 개인정보가 들어 있을 수 있다. 화면에 공개하지 않는다.
 */
@Entity
@Table(name = "file_upload")
public class FileUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 100, updatable = false)
    private String storedFileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private FileUploadStatus uploadStatus;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected FileUpload() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    /** 정상 저장된 파일의 기록을 만든다. */
    public static FileUpload uploaded(String originalFileName, String storedFileName, String filePath,
                                      long fileSize, String fileExtension, String contentType) {
        FileUpload f = new FileUpload();
        f.originalFileName = originalFileName;
        f.storedFileName = storedFileName;
        f.filePath = filePath;
        f.fileSize = fileSize;
        f.fileExtension = fileExtension;
        f.contentType = contentType;
        f.uploadStatus = FileUploadStatus.UPLOADED;
        return f;
    }

    public void markDeleted() {
        this.uploadStatus = FileUploadStatus.DELETED;
    }

    public Long getId() { return id; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public String getFilePath() { return filePath; }
    public long getFileSize() { return fileSize; }
    public String getFileExtension() { return fileExtension; }
    public String getContentType() { return contentType; }
    public FileUploadStatus getUploadStatus() { return uploadStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
