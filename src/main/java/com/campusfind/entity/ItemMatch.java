package com.campusfind.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="item_matches",uniqueConstraints=@UniqueConstraint(columnNames={"lost_item_id","found_item_id"}))
public class ItemMatch {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public Item lostItem;
    @ManyToOne(optional=false) public Item foundItem;
    public int score;
    public Instant createdAt=Instant.now();
}
