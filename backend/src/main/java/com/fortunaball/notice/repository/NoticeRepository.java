package com.fortunaball.notice.repository;

import com.fortunaball.notice.domain.Notice;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 손님용 목록: 공개 공지만, 최신순 */
    Page<Notice> findByVisibleTrueOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** 손님용 상세: 공개 공지만 */
    Optional<Notice> findByIdAndVisibleTrue(Long id);

    /** 관리자용 목록: 공개 여부로 거르기 */
    Page<Notice> findByVisibleOrderByCreatedAtDescIdDesc(boolean visible, Pageable pageable);

    /** 관리자용 목록: 전부 */
    Page<Notice> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
