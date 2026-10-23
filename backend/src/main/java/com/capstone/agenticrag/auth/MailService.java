package com.capstone.agenticrag.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.capstone.agenticrag.config.AppProperties;

/** Sends transactional email when SMTP is configured through MAIL_* environment variables. */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final boolean configured;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender, ObjectProvider<MailProperties> mailProperties,
                       AppProperties props) {
        this.sender = sender;
        MailProperties mp = mailProperties.getIfAvailable();
        this.configured = mp != null && StringUtils.hasText(mp.getHost()) && sender.getIfAvailable() != null;
        this.from = props.mail().from();
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean send(String to, String subject, String body) {
        if (!configured) {
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.getObject().send(message);
            return true;
        } catch (MailException e) {
            log.warn("Failed to send email: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
