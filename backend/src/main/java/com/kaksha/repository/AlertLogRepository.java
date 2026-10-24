package com.kaksha.repository;

import com.kaksha.domain.AlertLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

    boolean existsByEmailAndPrimaryIdAndSecondaryIdAndTcaBucket(
            String email, Long primaryId, Long secondaryId, Long tcaBucket);
}
