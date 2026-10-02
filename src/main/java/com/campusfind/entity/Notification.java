package com.campusfind.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="notifications")
public class Notification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public UserAccount user;
    @Column(length=150) public String title;
    @Column(length=1500) public String message;
    @Column(name="is_read") public boolean read;
    public Instant createdAt=Instant.now();
}
