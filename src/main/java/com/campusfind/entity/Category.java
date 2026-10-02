package com.campusfind.entity;

import jakarta.persistence.*;

@Entity @Table(name="categories")
public class Category {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(unique=true,nullable=false,length=80) public String name;
    public Category() {}
    public Category(String name) { this.name=name; }
}
