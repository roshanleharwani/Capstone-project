package contracts.dto;

import java.util.List;
import java.util.Map;

/**
 * Shared contract DTO: Output of ingress-shield module, input to context-compression module.
 * Contains sanitized (pseudonymized) chat messages and security metadata.
 */
public class SanitizedContext {

    private String sessionId;
    private String tenantId;
    private List<Message> messages;
    private Map<String, String> pseudonymMapping;
    private String dataClassification;
    private boolean hardRouteTier1;
    private Map<String, String> securityMetadata;

    public SanitizedContext() {}

    public SanitizedContext(String sessionId, String tenantId, List<Message> messages,
                            Map<String, String> pseudonymMapping, String dataClassification,
                            boolean hardRouteTier1, Map<String, String> securityMetadata) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.messages = messages;
        this.pseudonymMapping = pseudonymMapping;
        this.dataClassification = dataClassification;
        this.hardRouteTier1 = hardRouteTier1;
        this.securityMetadata = securityMetadata;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public Map<String, String> getPseudonymMapping() { return pseudonymMapping; }
    public void setPseudonymMapping(Map<String, String> pseudonymMapping) { this.pseudonymMapping = pseudonymMapping; }

    public String getDataClassification() { return dataClassification; }
    public void setDataClassification(String dataClassification) { this.dataClassification = dataClassification; }

    public boolean isHardRouteTier1() { return hardRouteTier1; }
    public void setHardRouteTier1(boolean hardRouteTier1) { this.hardRouteTier1 = hardRouteTier1; }

    public Map<String, String> getSecurityMetadata() { return securityMetadata; }
    public void setSecurityMetadata(Map<String, String> securityMetadata) { this.securityMetadata = securityMetadata; }

    public static class Message {
        private String role;
        private String content;

        public Message() {}
        public Message(String role, String content) { this.role = role; this.content = content; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
