package com.campusfind.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="claims",indexes={@Index(columnList="claimant_id"),@Index(columnList="item_id"),@Index(columnList="status")})
public class Claim {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public UserAccount claimant;
    @ManyToOne(optional=false) public Item item;
    @ManyToOne public Item lostItem;
    @Column(columnDefinition="text") public String answers;
    @Column(columnDefinition="text") public String serial;
    @Column(length=300) public String lossLocation;
    public int score;
    @Column(length=40) public String status="CLAIM_SUBMITTED";
    @Column(length=500) public String finderResponse;
    @Column(length=2000) public String reviewNote;
    public String handoverHash;
    @Column(length=1000) public String handoverCode;
    @Column(length=150) public String handoverLocation;
    public Instant codeExpiresAt;
    public int codeAttempts;
    public boolean finderConfirmed;
    public boolean claimantConfirmed;
    public Instant createdAt=Instant.now();
    public Instant updatedAt=Instant.now();
    public Instant returnedAt;
}
