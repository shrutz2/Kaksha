package com.kaksha.repository;

import com.kaksha.domain.ScreeningRun;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRunRepository extends JpaRepository<ScreeningRun, Long> {

    Optional<ScreeningRun> findFirstByModeOrderByRunAtDesc(String mode);

    /** Latest run of a mode that started inside a time range, for example "today". */
    Optional<ScreeningRun> findFirstByModeAndRunAtBetweenOrderByRunAtDesc(
            String mode, LocalDateTime from, LocalDateTime to);

    Optional<ScreeningRun> findFirstByModeAndPrimaryIdOrderByRunAtDesc(String mode, Long primaryId);
}
