package com.kaksha.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kaksha.domain.AlertLog;
import com.kaksha.domain.ConjunctionEvent;
import com.kaksha.domain.SpaceObject;
import com.kaksha.domain.WatchlistEntry;
import com.kaksha.repository.AlertLogRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.repository.WatchlistRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

    /** Remembers every email instead of sending it. */
    private static class RecordingSender implements EmailSender {
        final List<String> recipients = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        String failFor;

        @Override
        public void send(String to, String subject, String body) {
            if (to.equals(failFor)) {
                throw new IllegalStateException("mail server said no");
            }
            recipients.add(to);
            bodies.add(subject + "\n" + body);
        }
    }

    private WatchlistRepository watchlist;
    private AlertLogRepository alertLog;
    private SpaceObjectRepository objects;
    private RecordingSender sender;
    private AlertService service;
    private final List<AlertLog> logged = new ArrayList<>();

    @BeforeEach
    void setUp() {
        watchlist = mock(WatchlistRepository.class);
        alertLog = mock(AlertLogRepository.class);
        objects = mock(SpaceObjectRepository.class);
        sender = new RecordingSender();
        service = new AlertService(watchlist, alertLog, objects, sender, "https://kaksha.example");

        when(objects.findById(1L)).thenReturn(Optional.of(
                new SpaceObject(1L, "CARTOSAT-3", "2019-081A", "PAY", "+", "IND", null, 500.0, 520.0)));
        when(objects.findById(2L)).thenReturn(Optional.of(
                new SpaceObject(2L, "STARLINK-4256", "2021-001A", "PAY", "+", "US", null, 540.0, 550.0)));

        // The log answers "already sent" for anything that was saved earlier in the test.
        when(alertLog.existsByEmailAndPrimaryIdAndSecondaryIdAndTcaBucket(any(), any(), any(), any()))
                .thenAnswer(call -> logged.stream().anyMatch(l -> l.getEmail().equals(call.getArgument(0))
                        && l.getPrimaryId().equals(call.getArgument(1))
                        && l.getSecondaryId().equals(call.getArgument(2))
                        && l.getTcaBucket().equals(call.getArgument(3))));
        when(alertLog.save(any())).thenAnswer(call -> {
            logged.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    private static WatchlistEntry subscriber(String email, long noradId) {
        return new WatchlistEntry(email, noradId, "token-" + email, LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    private static ConjunctionEvent event(String risk, long primary, long secondary, int minute) {
        return new ConjunctionEvent(9L, primary, secondary, LocalDateTime.of(2026, 10, 24, 6, minute),
                0.48, 11.3, risk);
    }

    @Test
    void highRiskEventIsEmailedToEverySubscriberOfThatSatellite() {
        when(watchlist.findByNoradId(1L)).thenReturn(List.of(subscriber("a@x.com", 1), subscriber("b@x.com", 1)));

        int sent = service.sendHighRiskAlerts(List.of(event("HIGH", 1, 2, 51)));

        assertEquals(2, sent);
        assertEquals(List.of("a@x.com", "b@x.com"), sender.recipients);
    }

    @Test
    void mediumAndLowEventsAreNotEmailed() {
        when(watchlist.findByNoradId(1L)).thenReturn(List.of(subscriber("a@x.com", 1)));

        int sent = service.sendHighRiskAlerts(List.of(event("MEDIUM", 1, 2, 10), event("LOW", 1, 2, 40)));

        assertEquals(0, sent);
        assertTrue(sender.recipients.isEmpty());
    }

    @Test
    void sameApproachFoundAgainIsNotSentTwice() {
        when(watchlist.findByNoradId(1L)).thenReturn(List.of(subscriber("a@x.com", 1)));

        service.sendHighRiskAlerts(List.of(event("HIGH", 1, 2, 51)));
        // A later run finds the same approach a few minutes off, inside the same half hour.
        int second = service.sendHighRiskAlerts(List.of(event("HIGH", 1, 2, 55)));

        assertEquals(0, second);
        assertEquals(1, sender.recipients.size());
    }

    @Test
    void aFailingAddressDoesNotStopTheOthers() {
        when(watchlist.findByNoradId(1L)).thenReturn(List.of(subscriber("bad@x.com", 1), subscriber("good@x.com", 1)));
        sender.failFor = "bad@x.com";

        int sent = service.sendHighRiskAlerts(List.of(event("HIGH", 1, 2, 51)));

        assertEquals(1, sent);
        assertEquals(List.of("good@x.com"), sender.recipients);
    }

    @Test
    void emailNamesTheObjectsAndHasAnUnsubscribeLink() {
        when(watchlist.findByNoradId(1L)).thenReturn(List.of(subscriber("a@x.com", 1)));

        service.sendHighRiskAlerts(List.of(event("HIGH", 1, 2, 51)));

        String mail = sender.bodies.get(0);
        assertTrue(mail.contains("CARTOSAT-3"));
        assertTrue(mail.contains("STARLINK-4256"));
        assertTrue(mail.contains("0.48 km"));
        assertTrue(mail.contains("Owner:             United States"));
        assertTrue(mail.contains("Kind:              Active satellite"));
        assertTrue(mail.contains("its operator can move it"));
        assertTrue(mail.contains("https://kaksha.example/api/watchlist/unsubscribe/token-a@x.com"));
    }
}
