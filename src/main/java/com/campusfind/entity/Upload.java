package com.campusfind.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="uploads",indexes={@Index(columnList="itemId"),@Index(columnList="claimId")})
public class Upload {
    @Id @Column(length=36) public String id=UUID.randomUUID().toString();
    @ManyToOne(optional=false) public UserAccount owner;
    public Long itemId;
    public Long claimId;
    @Column(length=20) public String purpose;
    @Column(name="file_path",length=600) public String path;
    @Column(length=80) public String mimeType;
    @Column(length=200) public String originalName;
    public Instant createdAt=Instant.now();
}
