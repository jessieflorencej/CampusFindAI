package com.campusfind.repository;
import com.campusfind.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AuditRepository extends JpaRepository<AuditEvent,Long> {
    List<AuditEvent> findTop100ByOrderByCreatedAtDesc();
}
