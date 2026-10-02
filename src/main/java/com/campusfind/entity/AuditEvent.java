package com.campusfind.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long actorId;
    @Column(name="event_action",length=80) public String action;
    @Column(name="resource_name",length=200) public String resource;
    @Column(length=80) public String ip;
    @Column(name="event_result",length=100) public String result="SUCCESS";
    public Instant createdAt=Instant.now();
}
