package com.kaksha.alert;

/** Sends one plain-text email. Kept as an interface so tests do not need a mail server. */
public interface EmailSender {

    void send(String to, String subject, String body);
}
