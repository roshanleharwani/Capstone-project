package com.nexus.compression;

import com.nexus.compression.repository.ChatHistoryEntry;
import com.nexus.compression.repository.ChatHistoryRepository;
import com.nexus.compression.repository.VectorStoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VectorStoreRepositoryTest {

    private ChatHistoryRepository mockRepo;
    private VectorStoreRepository vectorStore;

    @BeforeEach
    void setUp() {
        mockRepo = Mockito.mock(ChatHistoryRepository.class);
        vectorStore = new VectorStoreRepository(mockRepo);
    }

    @Test
    @DisplayName("storeTurn should persist entry with correct session and role")
    void testStoreTurn() {
        when(mockRepo.countBySessionId("s1")).thenReturn(0);
        when(mockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        vectorStore.storeTurn("s1", "t1", "user", "Hello world", new float[]{0.1f, 0.2f});

        verify(mockRepo, times(1)).save(argThat(e ->
                "s1".equals(e.getSessionId()) && "user".equals(e.getRole())
        ));
    }

    @Test
    @DisplayName("hybridRetrieve should return recency window entries when no semantic candidates")
    void testHybridRetrieveReturnsRecencyEntries() {
        ChatHistoryEntry e1 = new ChatHistoryEntry("s1", "t1", "user", "Turn 1", "0.1,0.2", 0);
        ChatHistoryEntry e2 = new ChatHistoryEntry("s1", "t1", "assistant", "Turn 2", "0.3,0.4", 1);

        when(mockRepo.findRecentBySessionId(eq("s1"), anyInt())).thenReturn(List.of(e1, e2));
        when(mockRepo.findTopKSimilar(eq("s1"), anyString(), anyInt(), anyList())).thenReturn(List.of());

        List<ChatHistoryEntry> result = vectorStore.hybridRetrieve("s1", new float[]{0.1f, 0.2f});

        assertEquals(2, result.size());
        assertEquals("Turn 1", result.get(0).getContent());
    }

    @Test
    @DisplayName("MMR should select diverse entries from semantic candidates")
    void testMMRDiversitySelection() {
        // Two very similar candidates and one diverse candidate
        // Similar: both have embedding [1,0]
        // Diverse: embedding [0,1]
        ChatHistoryEntry similar1 = new ChatHistoryEntry("s1", "t1", "user", "A", "1.0,0.0", 0);
        ChatHistoryEntry similar2 = new ChatHistoryEntry("s1", "t1", "user", "B", "0.99,0.01", 1);
        ChatHistoryEntry diverse  = new ChatHistoryEntry("s1", "t1", "user", "C", "0.0,1.0", 2);

        when(mockRepo.findRecentBySessionId(eq("s1"), anyInt())).thenReturn(List.of());
        when(mockRepo.findTopKSimilar(eq("s1"), anyString(), anyInt(), anyList()))
                .thenReturn(List.of(similar1, similar2, diverse));

        // Query embedding aligned with similar1
        List<ChatHistoryEntry> result = vectorStore.hybridRetrieve("s1", new float[]{1.0f, 0.0f});

        // MMR should pick similar1 first (most relevant), then diverse (most different from selected)
        assertTrue(result.size() >= 2);
        boolean hasDiverse = result.stream().anyMatch(e -> "C".equals(e.getContent()));
        assertTrue(hasDiverse, "MMR should select the diverse entry to avoid redundancy");
    }

    @Test
    @DisplayName("csvToFloatArray should correctly parse comma-separated floats")
    void testCsvToFloatArray() {
        float[] result = vectorStore.csvToFloatArray("0.1,0.2,0.3");
        assertEquals(3, result.length);
        assertEquals(0.1f, result[0], 0.001f);
        assertEquals(0.3f, result[2], 0.001f);
    }
}
