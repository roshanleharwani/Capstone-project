package com.nexus.compression.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for chat history with pgvector hybrid retrieval support.
 *
 * Hybrid retrieval strategy:
 *   1. Recency Window  — last N turns by turn_index (temporal locality)
 *   2. Top-K MMR       — cosine similarity via pgvector <=> operator, deduplicated by MMR
 */
@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistoryEntry, Long> {

    /**
     * Recency Window: fetch the most recent `limit` turns for a session, ordered newest-first.
     */
    @Query(value = "SELECT * FROM chat_history WHERE session_id = :sessionId " +
                   "ORDER BY turn_index DESC LIMIT :limit",
           nativeQuery = true)
    List<ChatHistoryEntry> findRecentBySessionId(@Param("sessionId") String sessionId,
                                                  @Param("limit") int limit);

    /**
     * Top-K Semantic Search: cosine distance via pgvector <=> operator.
     * Returns the K nearest neighbours to the query embedding, excluding already-retrieved recency turns.
     *
     * NOTE: In production, `queryEmbedding` is cast to vector type.
     *       Here we use a TEXT cast for portability with the pgvector JDBC extension.
     */
    @Query(value = "SELECT * FROM chat_history " +
                   "WHERE session_id = :sessionId " +
                   "AND id NOT IN (:excludeIds) " +
                   "ORDER BY embedding::vector <=> CAST(:queryEmbedding AS vector) " +
                   "LIMIT :topK",
           nativeQuery = true)
    List<ChatHistoryEntry> findTopKSimilar(@Param("sessionId") String sessionId,
                                            @Param("queryEmbedding") String queryEmbedding,
                                            @Param("topK") int topK,
                                            @Param("excludeIds") List<Long> excludeIds);

    /** Fetch all turns for a session ordered by turn_index ascending (for context assembly). */
    List<ChatHistoryEntry> findBySessionIdOrderByTurnIndexAsc(String sessionId);

    /** Count turns in a session (used to assign next turn_index). */
    int countBySessionId(String sessionId);
}
