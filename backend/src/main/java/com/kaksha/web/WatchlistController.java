package com.kaksha.web;

import com.kaksha.service.WatchlistService;
import com.kaksha.web.dto.WatchlistRequest;
import com.kaksha.web.dto.WatchlistResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlist;

    public WatchlistController(WatchlistService watchlist) {
        this.watchlist = watchlist;
    }

    /** 201 for a new subscription, 200 if the same email was already watching the satellite. */
    @PostMapping
    public ResponseEntity<WatchlistResponse> subscribe(@Valid @RequestBody WatchlistRequest request) {
        WatchlistResponse response = watchlist.subscribe(request.email(), request.noradId());
        HttpStatus status = response.alreadySubscribed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /** Opened from the link in every alert email. */
    @GetMapping("/unsubscribe/{token}")
    public String unsubscribe(@PathVariable String token) {
        watchlist.unsubscribe(token);
        return "You have been unsubscribed.";
    }
}
