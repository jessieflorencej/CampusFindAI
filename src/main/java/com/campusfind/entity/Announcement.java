package com.campusfind.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="announcements")
public class Announcement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(length=150) public String title;
    @Column(length=2000) public String message;
    public Instant createdAt=Instant.now();
}
