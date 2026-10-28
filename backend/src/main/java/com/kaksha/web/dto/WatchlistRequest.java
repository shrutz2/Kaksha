package com.kaksha.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WatchlistRequest(
        @NotBlank @Email String email,
        @NotNull Long noradId) {
}
