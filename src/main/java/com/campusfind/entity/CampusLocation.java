package com.campusfind.entity;

import jakarta.persistence.*;

@Entity @Table(name="campus_locations")
public class CampusLocation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(unique=true,nullable=false,length=120) public String name;
    public CampusLocation() {}
    public CampusLocation(String name) { this.name=name; }
}
