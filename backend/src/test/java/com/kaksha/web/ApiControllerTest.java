package com.kaksha.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaksha.service.EventService;
import com.kaksha.service.GeometryService;
import com.kaksha.service.GlobeService;
import com.kaksha.service.SatelliteService;
import com.kaksha.service.ScreeningService;
import com.kaksha.service.StatsService;
import com.kaksha.service.WatchlistService;
import com.kaksha.web.dto.EventDto;
import com.kaksha.web.dto.GeometryDto;
import com.kaksha.web.dto.GlobeSnapshotDto;
import com.kaksha.web.dto.SatelliteDto;
import com.kaksha.web.dto.StatsDto;
import com.kaksha.web.dto.WatchlistResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class ApiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean EventService events;
    @MockBean SatelliteService satellites;
    @MockBean ScreeningService screening;
    @MockBean StatsService stats;
    @MockBean WatchlistService watchlist;
    @MockBean GeometryService geometry;
    @MockBean GlobeService globe;

    private static EventDto sampleEvent() {
        return new EventDto(1L, 44804L, "CARTOSAT-3", 29228L, "FENGYUN 1C DEB", "DEB", "China", "Debris",
                Instant.parse("2026-10-23T12:00:00Z"), 0.42, 8.8, "HIGH", 1, null, null, "NEW");
    }

    @Test
    void eventsAreReturnedForTheRequestedDate() throws Exception {
        when(events.eventsForDate(LocalDate.of(2026, 10, 23))).thenReturn(List.of(sampleEvent()));

        mvc.perform(get("/api/events").param("date", "2026-10-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].primaryName").value("CARTOSAT-3"))
                .andExpect(jsonPath("$[0].risk").value("HIGH"))
                .andExpect(jsonPath("$[0].missKm").value(0.42))
                .andExpect(jsonPath("$[0].secondaryOwner").value("China"))
                .andExpect(jsonPath("$[0].secondaryKind").value("Debris"));
    }

    @Test
    void badDateIsRejected() throws Exception {
        mvc.perform(get("/api/events").param("date", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void indianSatellitesAreListed() throws Exception {
        when(satellites.indianSatellites()).thenReturn(List.of(
                new SatelliteDto(44804L, "CARTOSAT-3", "PAY", null, 504.0, 509.0)));

        mvc.perform(get("/api/satellites/indian"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].noradId").value(44804))
                .andExpect(jsonPath("$[0].name").value("CARTOSAT-3"));
    }

    @Test
    void eventsOfOneSatelliteAreReturned() throws Exception {
        when(events.eventsForSatellite(44804L)).thenReturn(List.of(sampleEvent()));

        mvc.perform(get("/api/satellites/44804/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].secondaryId").value(29228));
    }

    @Test
    void screeningAnUnknownSatelliteGivesNotFound() throws Exception {
        when(screening.screenOne(anyLong())).thenThrow(new NoSuchElementException("No orbital data for object 1"));

        mvc.perform(post("/api/screen/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No orbital data for object 1"));
    }

    @Test
    void statsBeforeAnyRunGiveNotFound() throws Exception {
        when(stats.lastRun()).thenThrow(new NoSuchElementException("No daily screening run has finished yet"));

        mvc.perform(get("/api/stats/last-run"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statsAreReturned() throws Exception {
        when(stats.lastRun()).thenReturn(new StatsDto(Instant.parse("2026-10-23T02:30:00Z"), "DAILY", 50, 14000, 48,
                41000, 0, List.of(new StatsDto.Stage("All pairs", 700000, 0))));

        mvc.perform(get("/api/stats/last-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].name").value("All pairs"))
                .andExpect(jsonPath("$.totalRuntimeMs").value(41000));
    }

    @Test
    void newSubscriptionGivesCreated() throws Exception {
        when(watchlist.subscribe("a@b.com", 44804L)).thenReturn(new WatchlistResponse(1L, "a@b.com", 44804L, false));

        mvc.perform(post("/api/watchlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"noradId\":44804}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadySubscribed").value(false));
    }

    @Test
    void repeatedSubscriptionGivesOk() throws Exception {
        when(watchlist.subscribe(eq("a@b.com"), anyLong())).thenReturn(new WatchlistResponse(1L, "a@b.com", 44804L, true));

        mvc.perform(post("/api/watchlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"noradId\":44804}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(post("/api/watchlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"noradId\":44804}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void missingSatelliteIsRejected() throws Exception {
        mvc.perform(post("/api/watchlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void geometryOfAnEventIsReturned() throws Exception {
        when(geometry.forEvent(5L)).thenReturn(new GeometryDto(Instant.parse("2026-10-23T12:00:00Z"),
                List.of(new double[] {7000, 0, 0}, new double[] {0, 7000, 0}),
                List.of(new double[] {0, 0, 7000}),
                new double[] {7000, 0, 0}));

        mvc.perform(get("/api/events/5/geometry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryRing[1][1]").value(7000.0))
                .andExpect(jsonPath("$.meeting[0]").value(7000.0));
    }

    @Test
    void geometryOfAnUnknownEventIsNotFound() throws Exception {
        when(geometry.forEvent(99L)).thenThrow(new NoSuchElementException("Unknown event 99"));

        mvc.perform(get("/api/events/99/geometry"))
                .andExpect(status().isNotFound());
    }

    @Test
    void globeObjectsAreReturned() throws Exception {
        when(globe.snapshot()).thenReturn(new GlobeSnapshotDto(Instant.parse("2026-10-23T12:00:00Z"),
                List.of(new GlobeSnapshotDto.GlobeObject(44804L, "CARTOSAT-3", true,
                        new double[] {1.5, 2.5, 3.5}, new double[] {0.1, 0.2, 0.3}))));

        mvc.perform(get("/api/globe/objects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].name").value("CARTOSAT-3"))
                .andExpect(jsonPath("$.objects[0].indian").value(true))
                .andExpect(jsonPath("$.objects[0].p[2]").value(3.5));
    }

    @Test
    void unsubscribeLinkWorks() throws Exception {
        mvc.perform(get("/api/watchlist/unsubscribe/some-token"))
                .andExpect(status().isOk());

        verify(watchlist).unsubscribe("some-token");
    }

    @Test
    void unknownUnsubscribeTokenIsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new NoSuchElementException("This link is no longer valid"))
                .when(watchlist).unsubscribe("old-token");

        mvc.perform(get("/api/watchlist/unsubscribe/old-token"))
                .andExpect(status().isNotFound());
    }
}
