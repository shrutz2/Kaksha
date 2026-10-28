package com.kaksha.repository;

import com.kaksha.domain.ConjunctionEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConjunctionEventRepository extends JpaRepository<ConjunctionEvent, Long> {

    List<ConjunctionEvent> findByRunIdOrderByMissKmAsc(Long runId);

    List<ConjunctionEvent> findByPrimaryIdOrderByTcaAsc(Long primaryId);

    /**
     * Predictions of the same approach made by earlier daily runs: same two objects, closest
     * approach within a short time of the given one. The newest comes first.
     */
    @Query("select e from ConjunctionEvent e where e.daily = true and e.runId < :runId "
            + "and e.primaryId = :primaryId and e.secondaryId = :secondaryId "
            + "and e.tca between :from and :to order by e.runId desc")
    List<ConjunctionEvent> findEarlierPredictions(@Param("runId") Long runId, @Param("primaryId") Long primaryId,
                                                  @Param("secondaryId") Long secondaryId,
                                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Every prediction of one approach made up to and including the given run, oldest first.
     * It is the same search as above, but the run itself is included and the order is reversed.
     */
    @Query("select e from ConjunctionEvent e where e.daily = true and e.runId <= :runId "
            + "and e.primaryId = :primaryId and e.secondaryId = :secondaryId "
            + "and e.tca between :from and :to order by e.runId asc")
    List<ConjunctionEvent> findPredictionHistory(@Param("runId") Long runId, @Param("primaryId") Long primaryId,
                                                 @Param("secondaryId") Long secondaryId,
                                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
