package com.kaksha.web.dto;

import java.time.Instant;

/** What one screening run predicted for an approach: when it was made, and the result it gave. */
public record PredictionDto(Instant madeAt, double missKm, Instant tca) {
}
