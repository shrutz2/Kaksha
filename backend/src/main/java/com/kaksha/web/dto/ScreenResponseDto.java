package com.kaksha.web.dto;

import java.util.List;

/** The result of screening one satellite on request. */
public record ScreenResponseDto(
        Long noradId,
        int objectsChecked,
        int eventsFound,
        long runtimeMs,
        List<EventDto> events) {
}
