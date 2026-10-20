package com.fortunaball.common.file;

/** file_upload.upload_status 값. 문자열 그대로 DB 에 저장된다. */
public enum FileUploadStatus {
    UPLOADED,   // 정상 저장
    FAILED,     // 저장 실패 기록용
    DELETED     // 파일을 지움 (기록은 남김)
}
