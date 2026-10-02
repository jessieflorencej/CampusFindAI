package com.campusfind.repository;
import com.campusfind.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AnnouncementRepository extends JpaRepository<Announcement,Long> { List<Announcement> findTop10ByOrderByCreatedAtDesc(); }
