package com.ludoarena.service;

import com.ludoarena.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties props;

    public MailService(ObjectProvider<JavaMailSender> sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    public void sendPasswordReset(String to, String name, String link) {
        String body = "Hi " + name + ",\n\nUse the link below to choose a new Ludo Arena password. "
                + "It expires in 30 minutes and works once.\n\n" + link
                + "\n\nIf you did not request this, you can ignore this email.";
        if (props.mail().enabled()) {
            JavaMailSender s = sender.getIfAvailable();
            if (s == null) throw new IllegalStateException("MAIL_ENABLED=true but no mail sender is configured");
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.mail().from());
            m.setTo(to);
            m.setSubject("Reset your Ludo Arena password");
            m.setText(body);
            s.send(m);
        } else if (props.mail().logLinks()) {
            log.info("[DEV MAIL] password reset link for {}: {}", to, link);
        } else {
            log.warn("Mail is disabled; password reset email for {} was not sent", to);
        }
    }
}
