package com.fortunaball.common.file;

import org.springframework.data.jpa.repository.JpaRepository;

/** file_upload 테이블. 지금은 기본 저장·조회만 쓴다. */
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {
}
