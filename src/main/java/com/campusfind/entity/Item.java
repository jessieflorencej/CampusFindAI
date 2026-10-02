package com.campusfind.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="items",indexes={@Index(columnList="type,status"),@Index(columnList="category"),@Index(columnList="owner_id")})
public class Item {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public UserAccount owner;
    @Column(nullable=false,length=10) public String type;
    @Column(nullable=false,length=140) public String title;
    @Column(nullable=false,length=80) public String category;
    @Column(length=2500) public String description;
    @Column(length=100) public String brand;
    @Column(length=100) public String model;
    @Column(length=60) public String color;
    @Column(name="approximate_value",precision=12,scale=2) public BigDecimal value=BigDecimal.ZERO;
    @Column(name="event_date") public LocalDate date;
    @Column(name="event_time",length=20) public String time;
    @Column(length=120) public String location;
    @Column(length=120) public String building;
    @Column(name="building_floor",length=50) public String floor;
    @Column(length=100) public String room;
    @Column(length=200) public String lastSeen;
    @Column(length=150) public String storageLocation;
    @Column(columnDefinition="text") public String privateDetails;
    @Column(columnDefinition="text") public String serial;
    @Column(nullable=false,length=40) public String status;
    public Instant createdAt=Instant.now();
    public Instant updatedAt=Instant.now();
}
