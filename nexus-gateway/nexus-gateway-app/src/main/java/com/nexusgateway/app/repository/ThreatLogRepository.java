package com.nexusgateway.app.repository;

import com.nexusgateway.app.models.ThreatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ThreatLogRepository extends JpaRepository<ThreatLog, String> {
    
    List<ThreatLog> findTop10ByOrderByTimestampDesc();
    
    @Query("SELECT COUNT(t) FROM ThreatLog t WHERE t.timestamp >= :start AND t.timestamp < :end AND t.severity = :severity")
    long countBySeverityAndDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("severity") String severity);
}
