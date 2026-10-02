package com.campusfind.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="user_accounts", uniqueConstraints={@UniqueConstraint(columnNames="email"),@UniqueConstraint(columnNames="college_id")})
public class UserAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,length=100) public String name;
    @Column(nullable=false,length=190) public String email;
    @Column(name="college_id",nullable=false,length=60) public String collegeId;
    @Column(length=100) public String department;
    @Column(name="study_year",length=30) public String year;
    @Column(length=30) public String phone;
    @Column(nullable=false) public String passwordHash;
    @Column(nullable=false,length=15) public String role="USER";
    public boolean verified=false;
    public boolean emailVerified=false;
    public boolean identityVerified=false;
    public boolean active=true;
    public boolean flagged=false;
    public int reputation=0;
    public int failedAttempts=0;
    public int authVersion=0;
    public Instant lockedUntil;
    public Instant createdAt=Instant.now();
    public String profileImageId;
    public String verificationTokenHash;
    public Instant verificationExpiresAt;
    public String resetTokenHash;
    public Instant resetExpiresAt;
}
