package com.kaksha.repository;

import com.kaksha.domain.WatchlistEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {

    Optional<WatchlistEntry> findByEmailAndNoradId(String email, Long noradId);

    Optional<WatchlistEntry> findByToken(String token);

    List<WatchlistEntry> findByNoradId(Long noradId);
}
