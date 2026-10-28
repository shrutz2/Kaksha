package com.kaksha.web.dto;

public record WatchlistResponse(Long id, String email, Long noradId, boolean alreadySubscribed) {
}
