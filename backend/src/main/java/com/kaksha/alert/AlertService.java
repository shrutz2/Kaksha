package com.kaksha.alert;

import com.kaksha.domain.AlertLog;
import com.kaksha.domain.ConjunctionEvent;
import com.kaksha.domain.SpaceObject;
import com.kaksha.domain.WatchlistEntry;
import com.kaksha.repository.AlertLogRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.repository.WatchlistRepository;
import com.kaksha.screening.RiskLevel;
import com.kaksha.service.ObjectLabels;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Emails the people watching a satellite when a HIGH risk event is found for it. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long BUCKET_SECONDS = 30 * 60;

    private final WatchlistRepository watchlist;
    private final AlertLogRepository alertLog;
    private final SpaceObjectRepository objects;
    private final EmailSender sender;
    private final String publicUrl;

    public AlertService(WatchlistRepository watchlist, AlertLogRepository alertLog,
                        SpaceObjectRepository objects, EmailSender sender,
                        @Value("${kaksha.alerts.public-url}") String publicUrl) {
        this.watchlist = watchlist;
        this.alertLog = alertLog;
        this.objects = objects;
        this.sender = sender;
        this.publicUrl = publicUrl;
    }

    /** @return how many emails were sent */
    public int sendHighRiskAlerts(List<ConjunctionEvent> events) {
        int sent = 0;
        for (ConjunctionEvent event : events) {
            if (!RiskLevel.HIGH.name().equals(event.getRisk())) {
                continue;
            }
            for (WatchlistEntry subscriber : watchlist.findByNoradId(event.getPrimaryId())) {
                if (sendOnce(subscriber, event)) {
                    sent++;
                }
            }
        }
        if (sent > 0) {
            log.info("Sent {} high risk alert emails", sent);
        }
        return sent;
    }

    private boolean sendOnce(WatchlistEntry subscriber, ConjunctionEvent event) {
        long bucket = event.getTca().toEpochSecond(ZoneOffset.UTC) / BUCKET_SECONDS;
        if (alertLog.existsByEmailAndPrimaryIdAndSecondaryIdAndTcaBucket(
                subscriber.getEmail(), event.getPrimaryId(), event.getSecondaryId(), bucket)) {
            return false;
        }
        try {
            sender.send(subscriber.getEmail(), subject(event), body(subscriber, event));
        } catch (RuntimeException e) {
            // Not logged as sent, so the next run tries again. One bad address must not stop the others.
            log.warn("Could not send alert to {}: {}", subscriber.getEmail(), e.toString());
            return false;
        }
        alertLog.save(new AlertLog(subscriber.getEmail(), event.getPrimaryId(), event.getSecondaryId(),
                bucket, LocalDateTime.now(ZoneOffset.UTC)));
        return true;
    }

    private String subject(ConjunctionEvent event) {
        return "Kaksha alert: " + nameOf(event.getPrimaryId()) + " close approach on "
                + event.getTca().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " UTC";
    }

    private String body(WatchlistEntry subscriber, ConjunctionEvent event) {
        SpaceObject other = objects.findById(event.getSecondaryId()).orElse(null);
        String owner = other == null ? "not known" : ObjectLabels.ownerName(other.getOwner());
        String kind = other == null ? "not known" : ObjectLabels.kind(other);
        return "A close approach has been predicted for " + nameOf(event.getPrimaryId())
                + " (NORAD " + event.getPrimaryId() + ").\n\n"
                + "Other object:      " + nameOf(event.getSecondaryId()) + " (NORAD " + event.getSecondaryId() + ")\n"
                + "Owner:             " + owner + "\n"
                + "Kind:              " + kind + "\n"
                + "Closest approach:  " + event.getTca().format(TIME) + " UTC\n"
                + String.format("Miss distance:     %.2f km%n", event.getMissKm())
                + String.format("Relative speed:    %.1f km/s%n", event.getRelativeSpeedKmS())
                + "Risk level:        HIGH (miss distance below " + RiskLevel.HIGH_BELOW_KM + " km)\n\n"
                + advice(other) + "\n\n"
                + "This is a screening result from public orbital data. It has no uncertainty estimate\n"
                + "and is not a collision probability. It should not be used for flight decisions.\n\n"
                + "To stop these emails: " + publicUrl + "/api/watchlist/unsubscribe/" + subscriber.getToken() + "\n";
    }

    /** What the kind of the other object means for the next step. */
    private static String advice(SpaceObject other) {
        String steps = "Next step: confirm the result with better data (for example a conjunction message from "
                + "Space-Track) before deciding anything.";
        if (other != null && "Active satellite".equals(ObjectLabels.kind(other))) {
            return steps + " The other object is an active satellite, so its operator can move it, and may "
                    + "share its planned orbit with you.";
        }
        return steps + " The other object cannot manoeuvre, so any avoidance has to come from your side.";
    }

    private String nameOf(Long noradId) {
        return objects.findById(noradId).map(SpaceObject::getName).orElse("object " + noradId);
    }
}
