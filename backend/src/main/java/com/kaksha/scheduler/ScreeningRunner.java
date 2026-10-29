package com.kaksha.scheduler;

import com.kaksha.config.ScreeningProperties;
import com.kaksha.service.ScreeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs one full screening right after start-up when kaksha.screening.run-on-startup is true.
 * It runs after the catalog job (order 1), so the catalog is already in the database.
 */
@Component
@Order(2)
public class ScreeningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScreeningRunner.class);

    private final ScreeningService screening;
    private final ScreeningProperties props;

    public ScreeningRunner(ScreeningService screening, ScreeningProperties props) {
        this.screening = screening;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.runOnStartup()) {
            try {
                screening.screenAllIndian();
            } catch (RuntimeException e) {
                log.warn("Start-up screening skipped: {}", e.getMessage());
            }
        }
    }
}
