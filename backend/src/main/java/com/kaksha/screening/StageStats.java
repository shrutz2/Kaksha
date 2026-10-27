package com.kaksha.screening;

/** How many items entered and left one stage of the pipeline, and how long it took. */
public record StageStats(String name, long itemsIn, long itemsOut, long millis) {
}
