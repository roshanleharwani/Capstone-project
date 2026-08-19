package com.nexus.compression.repository;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Semantic Shadow Cache — Hybrid Retrieval Engine.
 *
 * Strategy:
 *   Phase 1 — Recency Window: always include the last RECENCY_WINDOW turns (temporal locality).
 *   Phase 2 — Top-K MMR: retrieve semantically similar turns not already in the recency window,
 *              then apply Maximal Marginal Relevance (MMR) to balance relevance vs. diversity.
 */
@Service
public class VectorStoreRepository {

    private static final int RECENCY_WINDOW = 6;
    private static final int TOP_K_CANDIDATES = 20;
    private static final int MMR_FINAL_K = 4;
    private static final double MMR_LAMBDA = 0.6; // relevance weight (1-lambda = diversity weight)

    private final ChatHistoryRepository chatHistoryRepository;

    public VectorStoreRepository(ChatHistoryRepository chatHistoryRepository) {
        this.chatHistoryRepository = chatHistoryRepository;
    }

    /**
     * Persist a new chat turn with its embedding into the Semantic Shadow Cache.
     */
    public void storeTurn(String sessionId, String tenantId, String role,
                          String content, float[] embedding) {
        int turnIndex = chatHistoryRepository.countBySessionId(sessionId);
        String embeddingCsv = floatArrayToCsv(embedding);
        ChatHistoryEntry entry = new ChatHistoryEntry(sessionId, tenantId, role, content, embeddingCsv, turnIndex);
        chatHistoryRepository.save(entry);
    }

    /**
     * Hybrid retrieval: Recency Window + Top-K MMR.
     *
     * @param sessionId      current session
     * @param queryEmbedding embedding of the current user query
     * @return ordered list of retrieved chat turns (recency first, then MMR-ranked semantic hits)
     */
    public List<ChatHistoryEntry> hybridRetrieve(String sessionId, float[] queryEmbedding) {
        // Phase 1: Recency Window
        List<ChatHistoryEntry> recentTurns = chatHistoryRepository.findRecentBySessionId(sessionId, RECENCY_WINDOW);
        Set<Long> recentIds = recentTurns.stream().map(ChatHistoryEntry::getId).collect(Collectors.toSet());

        // Phase 2: Top-K semantic candidates (excluding recency window entries)
        List<Long> excludeIds = recentIds.isEmpty() ? List.of(-1L) : new ArrayList<>(recentIds);
        String queryEmbeddingCsv = floatArrayToCsv(queryEmbedding);
        List<ChatHistoryEntry> semanticCandidates = chatHistoryRepository.findTopKSimilar(
                sessionId, queryEmbeddingCsv, TOP_K_CANDIDATES, excludeIds);

        // Phase 3: MMR re-ranking over semantic candidates
        List<ChatHistoryEntry> mmrSelected = applyMMR(semanticCandidates, queryEmbedding, MMR_FINAL_K);

        // Merge: recency (chronological) + MMR semantic hits
        List<ChatHistoryEntry> result = new ArrayList<>(recentTurns);
        result.addAll(mmrSelected);
        return result;
    }

    /**
     * Maximal Marginal Relevance (MMR) selection.
     * Balances relevance to query vs. diversity among selected items.
     *
     * MMR score = λ * sim(doc, query) - (1-λ) * max_sim(doc, selected)
     */
    private List<ChatHistoryEntry> applyMMR(List<ChatHistoryEntry> candidates,
                                             float[] queryEmbedding, int k) {
        if (candidates.isEmpty()) return Collections.emptyList();

        List<ChatHistoryEntry> selected = new ArrayList<>();
        List<ChatHistoryEntry> remaining = new ArrayList<>(candidates);

        while (selected.size() < k && !remaining.isEmpty()) {
            ChatHistoryEntry best = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (ChatHistoryEntry candidate : remaining) {
                float[] candidateEmb = csvToFloatArray(candidate.getEmbeddingCsv());
                double relevance = cosineSimilarity(candidateEmb, queryEmbedding);

                double maxSimilarityToSelected = 0.0;
                for (ChatHistoryEntry sel : selected) {
                    float[] selEmb = csvToFloatArray(sel.getEmbeddingCsv());
                    maxSimilarityToSelected = Math.max(maxSimilarityToSelected,
                            cosineSimilarity(candidateEmb, selEmb));
                }

                double mmrScore = MMR_LAMBDA * relevance - (1 - MMR_LAMBDA) * maxSimilarityToSelected;
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    best = candidate;
                }
            }

            if (best != null) {
                selected.add(best);
                remaining.remove(best);
            }
        }
        return selected;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    private String floatArrayToCsv(float[] arr) {
        if (arr == null || arr.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    float[] csvToFloatArray(String csv) {
        if (csv == null || csv.isBlank()) return new float[0];
        String[] parts = csv.split(",");
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Float.parseFloat(parts[i].trim());
        }
        return arr;
    }
}
