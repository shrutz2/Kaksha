package com.kaksha.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Stand-in used when no SMTP server is configured: the email is written to the log instead
 * of being sent, so the whole alert flow can be tried on a development machine.
 */
@Component
@ConditionalOnExpression("'${spring.mail.host:}' == ''")
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("No SMTP server configured, email not sent. To: {} Subject: {}\n{}", to, subject, body);
    }
}
