package com.nexus.compression.service;

import com.nexus.compression.repository.ChatHistoryEntry;
import com.nexus.compression.repository.VectorStoreRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Context Compression Pipeline Orchestrator.
 *
 * Pipeline stages:
 *   1. Store current turn in Semantic Shadow Cache (pgvector).
 *   2. Hybrid retrieval: Recency Window + Top-K MMR from chat history.
 *   3. Stratify prompt: isolate static prefix from dynamic payload.
 *   4. Extract protection floors (NER placeholders + RAG phrases).
 *   5. LLMLingua-2 single-pass compression of dynamic payload.
 *   6. Enforce protection floors on compressed output.
 *   7. Assemble and return CompressedPayloadDto.
 *
 * Input/output use module-local DTOs to avoid coupling to the contracts package at runtime.
 */
@Service
public class ContextCompressionService {

    private final VectorStoreRepository vectorStore;
    private final PromptStratifier stratifier;
    private final LLMLinguaCompressor compressor;
    private final ProtectionFloorEnforcer floorEnforcer;

    public ContextCompressionService(VectorStoreRepository vectorStore,
                                     PromptStratifier stratifier,
                                     LLMLinguaCompressor compressor,
                                     ProtectionFloorEnforcer floorEnforcer) {
        this.vectorStore = vectorStore;
        this.stratifier = stratifier;
        this.compressor = compressor;
        this.floorEnforcer = floorEnforcer;
    }

    // ── Module-local input DTO (mirrors contracts.dto.SanitizedContext) ──────────
    public static class SanitizedContextDto {
        private String sessionId;
        private String tenantId;
        private List<MessageDto> messages;
        private boolean hardRouteTier1;

        public SanitizedContextDto(String sessionId, String tenantId,
                                   List<MessageDto> messages, boolean hardRouteTier1) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.messages = messages;
            this.hardRouteTier1 = hardRouteTier1;
        }

        public String getSessionId() { return sessionId; }
        public String getTenantId() { return tenantId; }
        public List<MessageDto> getMessages() { return messages; }
        public boolean isHardRouteTier1() { return hardRouteTier1; }
    }

    public static class MessageDto {
        private final String role;
        private final String content;

        public MessageDto(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    // ── Module-local output DTO (mirrors contracts.dto.CompressedPayload) ────────
    public static class CompressedPayloadDto {
        private final String sessionId;
        private final String tenantId;
        private final String staticPrefix;
        private final String compressedDynamicContext;
        private final List<String> protectedPhrases;
        private final int originalTokenCount;
        private final int compressedTokenCount;
        private final double compressionRatio;
        private final Map<String, String> retrievedChunks;
        private final boolean hardRouteTier1;

        public CompressedPayloadDto(String sessionId, String tenantId, String staticPrefix,
                                    String compressedDynamicContext, List<String> protectedPhrases,
                                    int originalTokenCount, int compressedTokenCount,
                                    double compressionRatio, Map<String, String> retrievedChunks,
                                    boolean hardRouteTier1) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.staticPrefix = staticPrefix;
            this.compressedDynamicContext = compressedDynamicContext;
            this.protectedPhrases = protectedPhrases;
            this.originalTokenCount = originalTokenCount;
            this.compressedTokenCount = compressedTokenCount;
            this.compressionRatio = compressionRatio;
            this.retrievedChunks = retrievedChunks;
            this.hardRouteTier1 = hardRouteTier1;
        }

        public String getSessionId() { return sessionId; }
        public String getTenantId() { return tenantId; }
        public String getStaticPrefix() { return staticPrefix; }
        public String getCompressedDynamicContext() { return compressedDynamicContext; }
        public List<String> getProtectedPhrases() { return protectedPhrases; }
        public int getOriginalTokenCount() { return originalTokenCount; }
        public int getCompressedTokenCount() { return compressedTokenCount; }
        public double getCompressionRatio() { return compressionRatio; }
        public Map<String, String> getRetrievedChunks() { return retrievedChunks; }
        public boolean isHardRouteTier1() { return hardRouteTier1; }
    }

    /**
     * Main pipeline entry point.
     */
    public CompressedPayloadDto process(SanitizedContextDto input) {
        String sessionId = input.getSessionId();
        String tenantId = input.getTenantId();

        // 1. Store the latest user turn in the Semantic Shadow Cache
        MessageDto latestUserMsg = getLatestUserMessage(input.getMessages());
        float[] queryEmbedding = generateEmbedding(latestUserMsg != null ? latestUserMsg.getContent() : "");
        if (latestUserMsg != null) {
            vectorStore.storeTurn(sessionId, tenantId, latestUserMsg.getRole(),
                    latestUserMsg.getContent(), queryEmbedding);
        }

        // 2. Hybrid retrieval from Semantic Shadow Cache
        List<ChatHistoryEntry> retrieved = vectorStore.hybridRetrieve(sessionId, queryEmbedding);
        Map<String, String> retrievedChunks = buildRetrievedChunksMap(retrieved);

        // 3. Assemble full message list: retrieved history + current messages
        List<PromptStratifier.Message> allMessages = buildMessageList(input.getMessages(), retrieved);

        // 4. Stratify: static prefix vs dynamic payload
        PromptStratifier.StratifiedPrompt stratified = stratifier.stratify(allMessages);

        // 5. Extract protection floors from dynamic payload
        List<String> protectedPhrases = floorEnforcer.extractProtectedPhrases(stratified.getDynamicPayload());

        // 6. LLMLingua-2 single-pass compression
        LLMLinguaCompressor.CompressionResult compressionResult =
                compressor.compress(stratified.getDynamicPayload(), protectedPhrases);

        // 7. Enforce protection floors on compressed output
        String enforcedCompressed = floorEnforcer.enforceFloors(
                stratified.getDynamicPayload(), compressionResult.getCompressedText());

        return new CompressedPayloadDto(
                sessionId,
                tenantId,
                stratified.getStaticPrefix(),
                enforcedCompressed,
                protectedPhrases,
                compressionResult.getOriginalTokenCount(),
                compressionResult.getCompressedTokenCount(),
                compressionResult.getActualRatio(),
                retrievedChunks,
                input.isHardRouteTier1()
        );
    }

    private MessageDto getLatestUserMessage(List<MessageDto> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equalsIgnoreCase(messages.get(i).getRole())) {
                return messages.get(i);
            }
        }
        return messages.get(messages.size() - 1);
    }

    private List<PromptStratifier.Message> buildMessageList(List<MessageDto> current,
                                                             List<ChatHistoryEntry> retrieved) {
        List<PromptStratifier.Message> all = new ArrayList<>();
        // Prepend retrieved history (oldest first)
        for (ChatHistoryEntry entry : retrieved) {
            all.add(new PromptStratifier.Message(entry.getRole(), entry.getContent()));
        }
        // Append current request messages
        for (MessageDto msg : current) {
            all.add(new PromptStratifier.Message(msg.getRole(), msg.getContent()));
        }
        return all;
    }

    private Map<String, String> buildRetrievedChunksMap(List<ChatHistoryEntry> entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ChatHistoryEntry e : entries) {
            map.put("turn-" + e.getTurnIndex() + "-" + e.getRole(), e.getContent());
        }
        return map;
    }

    /**
     * Stub embedding generator.
     * In production, this calls a sentence-transformer sidecar (e.g. all-MiniLM-L6-v2).
     * Returns a deterministic pseudo-embedding based on character frequencies for testing.
     */
    float[] generateEmbedding(String text) {
        float[] embedding = new float[16]; // reduced dimension for stub
        if (text == null || text.isBlank()) return embedding;
        for (int i = 0; i < text.length() && i < embedding.length * 4; i++) {
            embedding[i % embedding.length] += (float) text.charAt(i) / 1000.0f;
        }
        // L2 normalise
        float norm = 0;
        for (float v : embedding) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) embedding[i] /= norm;
        }
        return embedding;
    }
}
