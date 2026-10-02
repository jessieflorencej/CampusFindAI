package com.campusfind.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

@Service
public class CampusMailService {
    private final JavaMailSender sender;
    private final String mode,from,outbox;
    public CampusMailService(JavaMailSender sender,@Value("${campus.mail-mode:outbox}") String mode,@Value("${campus.mail-from:campusfind@campus.edu}") String from,@Value("${campus.mail-outbox:.local/mail}") String outbox) {this.sender=sender;this.mode=mode;this.from=from;this.outbox=outbox;}
    public String mode() {return mode;}
    public void send(String to,String subject,String body) {
        if ("smtp".equalsIgnoreCase(mode)) {
            SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);message.setSubject(subject);message.setText(body);sender.send(message);
        } else if ("outbox".equalsIgnoreCase(mode)) {
            try {Path dir=Path.of(outbox);Files.createDirectories(dir);Files.writeString(dir.resolve(Instant.now().toEpochMilli()+"-"+UUID.randomUUID()+".txt"),"LOCAL DEMONSTRATION MAIL — not delivered externally\nTo: "+to+"\nSubject: "+subject+"\n\n"+body,StandardOpenOption.CREATE_NEW);}
            catch (Exception e) {throw new IllegalStateException("Unable to write local email outbox",e);}
        } else throw new IllegalStateException("campus.mail-mode must be smtp or outbox");
    }
}
