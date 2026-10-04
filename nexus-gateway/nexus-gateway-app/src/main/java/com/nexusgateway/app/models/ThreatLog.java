package com.nexusgateway.app.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "threat_logs")
public class ThreatLog {
    @Id
    private String id;
    private String type;
    private String severity;
    private String action;
    private LocalDateTime timestamp;

    public ThreatLog() {}

    public ThreatLog(String id, String type, String severity, String action, LocalDateTime timestamp) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.action = action;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
