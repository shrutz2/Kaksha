package com.kaksha.service;

import com.kaksha.domain.WatchlistEntry;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.repository.WatchlistRepository;
import com.kaksha.web.dto.WatchlistResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Stores who wants to be told about which satellite. */
@Service
public class WatchlistService {

    private final WatchlistRepository watchlist;
    private final SpaceObjectRepository objects;

    public WatchlistService(WatchlistRepository watchlist, SpaceObjectRepository objects) {
        this.watchlist = watchlist;
        this.objects = objects;
    }

    public WatchlistResponse subscribe(String email, long noradId) {
        if (!objects.existsById(noradId)) {
            throw new NoSuchElementException("Unknown satellite " + noradId);
        }
        String cleanEmail = email.trim().toLowerCase();

        // Subscribing twice is not an error, the second request just reports the first one.
        Optional<WatchlistEntry> existing = watchlist.findByEmailAndNoradId(cleanEmail, noradId);
        if (existing.isPresent()) {
            WatchlistEntry e = existing.get();
            return new WatchlistResponse(e.getId(), e.getEmail(), e.getNoradId(), true);
        }
        WatchlistEntry saved = watchlist.save(
                new WatchlistEntry(cleanEmail, noradId, UUID.randomUUID().toString(),
                        LocalDateTime.now(ZoneOffset.UTC)));
        return new WatchlistResponse(saved.getId(), saved.getEmail(), saved.getNoradId(), false);
    }

    /** Removes a subscription using the secret token from the email link. */
    public void unsubscribe(String token) {
        WatchlistEntry entry = watchlist.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("This link is no longer valid"));
        watchlist.delete(entry);
    }
}
