package com.fortunaball.admin.auth.repository;

import com.fortunaball.admin.auth.domain.AdminUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByLoginId(String loginId);
}
