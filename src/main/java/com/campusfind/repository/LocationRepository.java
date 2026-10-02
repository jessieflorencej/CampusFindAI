package com.campusfind.repository;
import com.campusfind.entity.CampusLocation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LocationRepository extends JpaRepository<CampusLocation,Long> { boolean existsByName(String name); }
