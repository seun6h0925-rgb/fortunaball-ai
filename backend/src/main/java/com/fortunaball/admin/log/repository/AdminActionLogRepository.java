package com.fortunaball.admin.log.repository;

import com.fortunaball.admin.log.domain.AdminActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {
}
