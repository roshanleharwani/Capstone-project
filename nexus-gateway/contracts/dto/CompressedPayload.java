package contracts.dto;

import java.util.List;
import java.util.Map;

/**
 * Shared contract DTO: Output of context-compression module.
 * Contains the compressed prompt payload ready for the LLM router.
 */
public class CompressedPayload {

    private String sessionId;
    private String tenantId;
    private String staticPrefix;
    private String compressedDynamicContext;
    private List<String> protectedPhrases;
    private int originalTokenCount;
    private int compressedTokenCount;
    private double compressionRatio;
    private Map<String, String> retrievedChunks;
    private boolean hardRouteTier1;

    public CompressedPayload() {}

    public CompressedPayload(String sessionId, String tenantId, String staticPrefix,
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
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getStaticPrefix() { return staticPrefix; }
    public void setStaticPrefix(String staticPrefix) { this.staticPrefix = staticPrefix; }

    public String getCompressedDynamicContext() { return compressedDynamicContext; }
    public void setCompressedDynamicContext(String compressedDynamicContext) { this.compressedDynamicContext = compressedDynamicContext; }

    public List<String> getProtectedPhrases() { return protectedPhrases; }
    public void setProtectedPhrases(List<String> protectedPhrases) { this.protectedPhrases = protectedPhrases; }

    public int getOriginalTokenCount() { return originalTokenCount; }
    public void setOriginalTokenCount(int originalTokenCount) { this.originalTokenCount = originalTokenCount; }

    public int getCompressedTokenCount() { return compressedTokenCount; }
    public void setCompressedTokenCount(int compressedTokenCount) { this.compressedTokenCount = compressedTokenCount; }

    public double getCompressionRatio() { return compressionRatio; }
    public void setCompressionRatio(double compressionRatio) { this.compressionRatio = compressionRatio; }

    public Map<String, String> getRetrievedChunks() { return retrievedChunks; }
    public void setRetrievedChunks(Map<String, String> retrievedChunks) { this.retrievedChunks = retrievedChunks; }

    public boolean isHardRouteTier1() { return hardRouteTier1; }
    public void setHardRouteTier1(boolean hardRouteTier1) { this.hardRouteTier1 = hardRouteTier1; }
}
