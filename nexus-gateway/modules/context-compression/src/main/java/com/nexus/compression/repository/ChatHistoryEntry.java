package com.nexus.compression.repository;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a single chat turn stored in PostgreSQL with a pgvector embedding column.
 * The `embedding` column is a native float[] mapped via pgvector's vector type.
 */
@Entity
@Table(name = "chat_history")
public class ChatHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Sentence embedding stored as a PostgreSQL vector(1536) column.
     * Serialized as a comma-separated float string for portability.
     * In production, use pgvector's PGvector type via native queries.
     */
    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embeddingCsv;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    public ChatHistoryEntry() {}

    public ChatHistoryEntry(String sessionId, String tenantId, String role,
                            String content, String embeddingCsv, int turnIndex) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.role = role;
        this.content = content;
        this.embeddingCsv = embeddingCsv;
        this.turnIndex = turnIndex;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmbeddingCsv() { return embeddingCsv; }
    public void setEmbeddingCsv(String embeddingCsv) { this.embeddingCsv = embeddingCsv; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getTurnIndex() { return turnIndex; }
    public void setTurnIndex(int turnIndex) { this.turnIndex = turnIndex; }
}
