package com.nexus.compression;

import com.nexus.compression.repository.ChatHistoryEntry;
import com.nexus.compression.repository.VectorStoreRepository;
import com.nexus.compression.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContextCompressionServiceTest {

    private VectorStoreRepository mockVectorStore;
    private ContextCompressionService service;

    @BeforeEach
    void setUp() {
        mockVectorStore = Mockito.mock(VectorStoreRepository.class);
        PromptStratifier stratifier = new PromptStratifier();
        LLMLinguaCompressor compressor = new LLMLinguaCompressor();
        compressor.setTargetCompressionRatio(0.5);
        ProtectionFloorEnforcer floorEnforcer = new ProtectionFloorEnforcer();

        service = new ContextCompressionService(mockVectorStore, stratifier, compressor, floorEnforcer);
    }

    @Test
    @DisplayName("Pipeline should return CompressedPayloadDto with correct sessionId and tenantId")
    void testPipelinePreservesSessionAndTenant() {
        when(mockVectorStore.hybridRetrieve(eq("sess-1"), any())).thenReturn(List.of());
        doNothing().when(mockVectorStore).storeTurn(anyString(), anyString(), anyString(), anyString(), any());

        ContextCompressionService.SanitizedContextDto input = new ContextCompressionService.SanitizedContextDto(
                "sess-1", "tenant-cs",
                List.of(new ContextCompressionService.MessageDto("user", "Explain quantum entanglement.")),
                false
        );

        ContextCompressionService.CompressedPayloadDto result = service.process(input);

        assertEquals("sess-1", result.getSessionId());
        assertEquals("tenant-cs", result.getTenantId());
        assertNotNull(result.getCompressedDynamicContext());
    }

    @Test
    @DisplayName("Pipeline should preserve NER placeholders through compression")
    void testPipelinePreservesNERPlaceholders() {
        when(mockVectorStore.hybridRetrieve(anyString(), any())).thenReturn(List.of());
        doNothing().when(mockVectorStore).storeTurn(anyString(), anyString(), anyString(), anyString(), any());

        String userContent = "Please send the invoice to {{EMAIL_1}} and cc {{PERSON_1}} on the thread.";
        ContextCompressionService.SanitizedContextDto input = new ContextCompressionService.SanitizedContextDto(
                "sess-2", "tenant-bio",
                List.of(new ContextCompressionService.MessageDto("user", userContent)),
                false
        );

        ContextCompressionService.CompressedPayloadDto result = service.process(input);

        assertTrue(result.getCompressedDynamicContext().contains("{{EMAIL_1}}"),
                "EMAIL placeholder must survive compression");
        assertTrue(result.getCompressedDynamicContext().contains("{{PERSON_1}}"),
                "PERSON placeholder must survive compression");
    }

    @Test
    @DisplayName("Pipeline should isolate system message into static prefix")
    void testPipelineStratifiesSystemMessage() {
        when(mockVectorStore.hybridRetrieve(anyString(), any())).thenReturn(List.of());
        doNothing().when(mockVectorStore).storeTurn(anyString(), anyString(), anyString(), anyString(), any());

        ContextCompressionService.SanitizedContextDto input = new ContextCompressionService.SanitizedContextDto(
                "sess-3", "tenant-admin",
                List.of(
                        new ContextCompressionService.MessageDto("system", "You are a secure enterprise assistant."),
                        new ContextCompressionService.MessageDto("user", "Summarise the quarterly report.")
                ),
                false
        );

        ContextCompressionService.CompressedPayloadDto result = service.process(input);

        assertTrue(result.getStaticPrefix().contains("You are a secure enterprise assistant."),
                "System message must be in static prefix");
        assertFalse(result.getCompressedDynamicContext().contains("You are a secure enterprise assistant."),
                "System message must NOT appear in dynamic context");
    }

    @Test
    @DisplayName("Pipeline should include retrieved history chunks in output map")
    void testPipelineIncludesRetrievedChunks() {
        ChatHistoryEntry historyEntry = new ChatHistoryEntry(
                "sess-4", "tenant-cs", "user", "Previous question about budgets", "0.1,0.2", 0);

        when(mockVectorStore.hybridRetrieve(eq("sess-4"), any())).thenReturn(List.of(historyEntry));
        doNothing().when(mockVectorStore).storeTurn(anyString(), anyString(), anyString(), anyString(), any());

        ContextCompressionService.SanitizedContextDto input = new ContextCompressionService.SanitizedContextDto(
                "sess-4", "tenant-cs",
                List.of(new ContextCompressionService.MessageDto("user", "What was the budget decision?")),
                false
        );

        ContextCompressionService.CompressedPayloadDto result = service.process(input);

        assertFalse(result.getRetrievedChunks().isEmpty(), "Retrieved chunks map should not be empty");
        assertTrue(result.getRetrievedChunks().values().stream()
                .anyMatch(v -> v.contains("budgets")));
    }

    @Test
    @DisplayName("hardRouteTier1 flag should be propagated to output")
    void testHardRouteFlagPropagated() {
        when(mockVectorStore.hybridRetrieve(anyString(), any())).thenReturn(List.of());
        doNothing().when(mockVectorStore).storeTurn(anyString(), anyString(), anyString(), anyString(), any());

        ContextCompressionService.SanitizedContextDto input = new ContextCompressionService.SanitizedContextDto(
                "sess-5", "tenant-restricted",
                List.of(new ContextCompressionService.MessageDto("user", "Confidential strategy document.")),
                true
        );

        ContextCompressionService.CompressedPayloadDto result = service.process(input);

        assertTrue(result.isHardRouteTier1(), "hardRouteTier1 flag must be propagated");
    }
}
